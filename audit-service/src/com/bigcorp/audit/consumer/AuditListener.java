package com.bigcorp.audit.consumer;

import com.bigcorp.audit.dao.AuditDAO;
import com.bigcorp.audit.dao.BillingDAO;
import com.bigcorp.common.billing.CommissionCalculator;
import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.model.AuditEvent;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.common.xml.XmlHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

/**
 * Standalone JMS consumer for the audit service.
 * 
 * Polls BIGCORP.TRADE.CONFIRMATIONS for order status updates,
 * writes AUDIT_LOG entries, and creates BILLING_LEDGER entries
 * for filled orders (charging tier-based commission to the client).
 * 
 * Modeled on NotificationListener: poll MessageQueueHelper.receiveMessage,
 * unmarshal XML with XmlHelper, process, persist via ConnectionHelper,
 * and never let one bad message kill the loop.
 * 
 * @author Audit team
 * @since 2.1
 */
public class AuditListener {

    private boolean running = false;

    private static final long POLL_TIMEOUT_MS = 5000;

    private AuditDAO auditDAO;
    private BillingDAO billingDAO;

    public AuditListener() {
        this.auditDAO = new AuditDAO();
        this.billingDAO = new BillingDAO();
    }

    /**
     * Start listening for trade confirmation messages.
     * This method blocks until stop() is called from another thread.
     */
    public void startListening() {
        running = true;
        System.out.println("AuditListener started, polling " + MessageQueueHelper.QUEUE_TRADE_CONFIRMATIONS);

        while (running) {
            try {
                String messageXml = MessageQueueHelper.receiveMessage(
                        MessageQueueHelper.QUEUE_TRADE_CONFIRMATIONS, POLL_TIMEOUT_MS);

                if (messageXml == null) {
                    continue;
                }

                System.out.println("Audit: received confirmation message (" + messageXml.length() + " chars)");

                TradeOrder order = XmlHelper.unmarshalTradeOrder(messageXml);
                if (order == null) {
                    System.err.println("ERROR: Audit: failed to unmarshal trade order, message discarded");
                    continue;
                }

                processOrderEvent(order);

            } catch (Exception e) {
                System.err.println("ERROR: Exception in audit listener loop: " + e.getMessage());
                e.printStackTrace();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.out.println("AuditListener stopped.");
    }

    /**
     * Process an order event: write audit log and billing entry.
     */
    private void processOrderEvent(TradeOrder order) {
        String eventType = deriveEventType(order.getStatus());

        // Build audit event
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setSourceSystem(AuditEvent.SOURCE_ORDER_ENGINE);
        event.setEntityType(AuditEvent.ENTITY_ORDER);
        event.setEntityId(order.getOrderId());
        event.setLogDate(new Date());
        event.setUserId(order.getClientId());

        // Build description summarizing the order
        StringBuffer desc = new StringBuffer();
        desc.append("client=").append(order.getClientId());
        desc.append(" symbol=").append(order.getSymbol());
        desc.append(" qty=").append(order.getQuantity());
        desc.append(" side=").append(order.getSide());
        desc.append(" price=").append(order.getPrice());
        if (order.getStatus() != null) {
            desc.append(" status=").append(order.getStatus());
        }
        event.setDescription(desc.toString());

        // Save audit log entry
        auditDAO.insertAuditEvent(event);

        // For filled orders, also create a billing ledger entry
        if (TradeOrder.STATUS_FILLED.equals(order.getStatus())) {
            createBillingEntry(order);
        }
    }

    /**
     * Create a billing ledger entry charging tier-based commission.
     */
    private void createBillingEntry(TradeOrder order) {
        try {
            String clientTier = lookupClientTier(order.getClientId());
            double grossAmount = order.getQuantity() * order.getPrice();
            double commission = CommissionCalculator.calculate(grossAmount, clientTier);
            double netAmount = grossAmount + commission;

            billingDAO.insertBillingEntry(
                    order.getOrderId(),
                    order.getClientId(),
                    grossAmount,
                    commission,
                    netAmount);

            // Also log the billing event in audit
            AuditEvent billingEvent = new AuditEvent();
            billingEvent.setEventType(AuditEvent.EVENT_BILLING_CHARGED);
            billingEvent.setSourceSystem(AuditEvent.SOURCE_AUDIT_SERVICE);
            billingEvent.setEntityType(AuditEvent.ENTITY_BILLING);
            billingEvent.setEntityId(order.getOrderId());
            billingEvent.setLogDate(new Date());
            billingEvent.setUserId(order.getClientId());

            StringBuffer desc = new StringBuffer();
            desc.append("gross=").append(grossAmount);
            desc.append(" commission=").append(commission);
            desc.append(" net=").append(netAmount);
            desc.append(" tier=").append(clientTier != null ? clientTier : "DEFAULT");
            billingEvent.setDescription(desc.toString());

            auditDAO.insertAuditEvent(billingEvent);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to create billing entry for " 
                    + order.getOrderId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Look up client tier from the CLIENTS table.
     */
    private String lookupClientTier(String clientId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            ps = conn.prepareStatement("SELECT TIER FROM CLIENTS WHERE CLIENT_ID = ?");
            ps.setString(1, clientId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("TIER");
            }
            return null;
        } catch (Exception e) {
            System.err.println("WARN: Could not look up tier for client " + clientId + ": " + e.getMessage());
            return null;
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Derive the audit event type from the order status.
     */
    private String deriveEventType(String orderStatus) {
        if (TradeOrder.STATUS_FILLED.equals(orderStatus)) {
            return AuditEvent.EVENT_ORDER_FILLED;
        } else if (TradeOrder.STATUS_REJECTED.equals(orderStatus)) {
            return AuditEvent.EVENT_ORDER_REJECTED;
        } else if (TradeOrder.STATUS_SETTLED.equals(orderStatus)) {
            return AuditEvent.EVENT_ORDER_SETTLED;
        }
        return "ORDER_" + (orderStatus != null ? orderStatus : "UNKNOWN");
    }

    /**
     * Stop the listener gracefully.
     */
    public void stop() {
        running = false;
    }
}
