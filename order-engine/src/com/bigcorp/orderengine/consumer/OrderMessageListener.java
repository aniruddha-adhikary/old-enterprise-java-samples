package com.bigcorp.orderengine.consumer;

import com.bigcorp.common.billing.CommissionCalculator;
import com.bigcorp.common.model.Client;
import com.bigcorp.common.model.Notification;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.rules.RuleEngine;
import com.bigcorp.common.rules.impl.ClientTierRule;
import com.bigcorp.common.rules.impl.MarketHoursRule;
import com.bigcorp.common.rules.impl.MaxOrderValueRule;
import com.bigcorp.common.rules.impl.SpecialClientsRule;
import com.bigcorp.common.xml.XmlHelper;
import com.bigcorp.orderengine.dao.OrderDAO;
import com.bigcorp.orderengine.soap.PricingServiceClient;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Date;

/**
 * JMS message listener for incoming trade orders.
 * 
 * Polls the TRADE_ORDERS queue for new order messages, validates them
 * through the rule engine, gets pricing, and sends confirmation or
 * rejection notifications.
 * 
 * This was originally supposed to use MessageListener interface with
 * onMessage() but "the async callback kept losing messages on the 
 * WebLogic cluster" so we switched to polling. 
 * 
 * Known issues:
 * - JIRA-2456: Price validation is done both in the rule engine AND 
 *   manually here. The manual check should probably be removed but
 *   "it caught a bug once" so nobody wants to touch it.
 * - JIRA-2501: Commission rate is hardcoded. Should come from client
 *   tier config. "Will fix in next sprint." (that was 2001)
 * 
 * @author Bob
 * @author Steve (notification integration - 2001-07)
 * @since 1.0
 */
public class OrderMessageListener {

    // JIRA-2501: commission rate is now derived from client tier
    // via CommissionCalculator (was hardcoded as 0.02)

    // poll interval in milliseconds
    // was 1000 but increased to 5000 after the CPU usage incident (JIRA-2340)
    private static final long POLL_TIMEOUT_MS = 5000;

    // maximum price deviation allowed (10%) - checked MANUALLY in addition
    // to the rule engine check because "belt and suspenders" - Bob
    private static final double MAX_PRICE_DEVIATION = 0.10;

    private boolean running = false;
    private OrderDAO orderDAO;
    private PricingServiceClient pricingClient;
    private RuleEngine ruleEngine;

    // JMS resources - created once, reused
    private Connection jmsConnection;
    private Session jmsSession;
    private MessageConsumer jmsConsumer;

    public OrderMessageListener() {
        this.orderDAO = new OrderDAO();
        this.pricingClient = new PricingServiceClient();
        this.ruleEngine = RuleEngine.getInstance();
    }

    /**
     * Initialize the rule engine with all business rules.
     * 
     * Rules are added in priority order (sort of - see RuleEngine 
     * javadoc about the backwards priority bug).
     */
    private void initRules() {
        System.out.println("Initializing rule engine...");
        ruleEngine.addRule(new MaxOrderValueRule());
        ruleEngine.addRule(new ClientTierRule());
        ruleEngine.addRule(new MarketHoursRule());
        ruleEngine.addRule(new SpecialClientsRule());
        System.out.println("Rule engine initialized with " + ruleEngine.getRuleCount() + " rules");
    }

    /**
     * Connect to JMS and start consuming messages.
     * 
     * This method blocks until stop() is called. It creates a JMS
     * connection, session, and consumer, then polls for messages
     * in a loop.
     */
    public void startListening() {
        System.out.println("OrderMessageListener starting...");

        // init rules
        initRules();

        running = true;

        try {
            // connect to ActiveMQ
            ConnectionFactory cf = MessageQueueHelper.getConnectionFactory();
            jmsConnection = cf.createConnection();
            jmsConnection.start();

            jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = jmsSession.createQueue(MessageQueueHelper.QUEUE_TRADE_ORDERS);
            jmsConsumer = jmsSession.createConsumer(destination);

            System.out.println("Connected to queue: " + MessageQueueHelper.QUEUE_TRADE_ORDERS);
            System.out.println("Listening for order messages...");

            // main polling loop
            while (running) {
                try {
                    javax.jms.Message message = jmsConsumer.receive(POLL_TIMEOUT_MS);

                    if (message != null && message instanceof TextMessage) {
                        String messageText = ((TextMessage) message).getText();
                        System.out.println("Received message (" + messageText.length() + " chars)");

                        processOrder(messageText);
                    }
                    // else: timeout, no message - keep polling

                } catch (Exception e) {
                    // don't let one bad message kill the listener
                    System.err.println("ERROR processing message: " + e.getMessage());
                    e.printStackTrace();

                    // sleep a bit before retrying to avoid tight error loops
                    // (learned this the hard way during the 2001 ActiveMQ outage)
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

        } catch (JMSException e) {
            System.err.println("FATAL: JMS connection error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to start JMS listener", e);
        } finally {
            cleanup();
        }

        System.out.println("OrderMessageListener stopped.");
    }

    /**
     * Process a single order message.
     * 
     * Steps:
     * 1. Unmarshal XML to TradeOrder
     * 2. Look up client from DB
     * 3. Run rule engine
     * 4. Get price quote
     * 5. Manual price deviation check (yes, redundant with rules)
     * 6. Update order status
     * 7. Send notifications
     */
    private void processOrder(String orderXml) {
        System.out.println("--- Processing order ---");

        // 1. unmarshal
        TradeOrder order = XmlHelper.unmarshalTradeOrder(orderXml);
        if (order == null) {
            System.err.println("ERROR: Failed to unmarshal order XML, skipping");
            return;
        }
        System.out.println("Order: " + order.toString());

        // 2. look up client
        Client client = orderDAO.findClient(order.getClientId());
        if (client == null) {
            System.err.println("ERROR: Client not found: " + order.getClientId());
            rejectOrder(order, "Client not found: " + order.getClientId(), null);
            return;
        }
        System.out.println("Client: " + client.toString());

        // check if client is active
        if (!client.isActive()) {
            System.err.println("WARN: Client " + client.getClientId() + " is inactive");
            rejectOrder(order, "Client account is inactive", client);
            return;
        }

        // 3. run rule engine
        RuleContext context = new RuleContext(order, client);
        boolean rulesPassed = ruleEngine.evaluate(context);

        if (!rulesPassed) {
            String reason = context.getRejectionReason();
            System.out.println("Order REJECTED by rule engine: " + reason);
            rejectOrder(order, reason, client);
            return;
        }
        System.out.println("Order passed all rules");

        // 4. get price quote from pricing service
        double quotedPrice = pricingClient.getQuote(order.getSymbol());
        if (quotedPrice <= 0) {
            System.err.println("ERROR: Could not get price for symbol: " + order.getSymbol());
            rejectOrder(order, "Price unavailable for symbol: " + order.getSymbol(), client);
            return;
        }
        System.out.println("Quoted price for " + order.getSymbol() + ": " + quotedPrice);

        // 5. manual price deviation check
        // JIRA-2456: this duplicates what the rule engine does, but Bob
        // insists we keep it "just in case the rules miss something"
        if (order.getRequestedPrice() > 0) {
            double deviation = Math.abs(quotedPrice - order.getRequestedPrice()) / order.getRequestedPrice();
            if (deviation > MAX_PRICE_DEVIATION) {
                System.out.println("Price deviation too high: " + (deviation * 100) + "% " +
                        "(requested=" + order.getRequestedPrice() + ", quoted=" + quotedPrice + ")");
                rejectOrder(order, "Price deviation exceeds " + (MAX_PRICE_DEVIATION * 100) + "% limit", client);
                return;
            }
        }

        // 6. fill the order
        double totalValue = order.getQuantity() * quotedPrice;
        double commission = CommissionCalculator.calculate(totalValue, client);
        // double netAmount = totalValue + commission; // not used yet - for settlement

        order.setPrice(quotedPrice);
        order.setStatus(TradeOrder.STATUS_FILLED);
        order.setLastModified(new Date());

        // add a note about the fill
        StringBuffer notesBuf = new StringBuffer();
        if (order.getNotes() != null) {
            notesBuf.append(order.getNotes());
            notesBuf.append("; ");
        }
        notesBuf.append("Filled at ");
        notesBuf.append(quotedPrice);
        notesBuf.append(", commission=");
        notesBuf.append(commission);
        // notesBuf.append(", net=");
        // notesBuf.append(netAmount);
        order.setNotes(notesBuf.toString());

        // save to DB
        orderDAO.saveOrder(order);

        // also update via the status method (redundant, but the 
        // settlement batch job reads from this update path)
        orderDAO.updateOrderStatus(order.getOrderId(), TradeOrder.STATUS_FILLED, quotedPrice);

        System.out.println("Order FILLED: " + order.getOrderId() + " @ " + quotedPrice);

        // 7. send confirmation notification
        sendConfirmationNotification(order, client, quotedPrice, commission);

        // 8. send status update to confirmations queue
        sendStatusUpdate(order);

        System.out.println("--- Order processing complete ---");
    }

    /**
     * Reject an order and send rejection notification.
     */
    private void rejectOrder(TradeOrder order, String reason, Client client) {
        order.setStatus(TradeOrder.STATUS_REJECTED);
        order.setLastModified(new Date());
        order.setNotes(reason);

        // save rejected order
        try {
            orderDAO.saveOrder(order);
            orderDAO.updateOrderStatus(order.getOrderId(), TradeOrder.STATUS_REJECTED, 0.0);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to save rejected order: " + e.getMessage());
            // don't throw - still try to send the notification
        }

        // send rejection notification
        if (client != null && client.getEmail() != null) {
            sendRejectionNotification(order, client, reason);
        }

        // send status update to confirmations queue
        sendStatusUpdate(order);
    }

    /**
     * Send an order confirmation notification to the notifications queue.
     */
    private void sendConfirmationNotification(TradeOrder order, Client client, 
            double price, double commission) {
        try {
            Notification notif = new Notification();
            notif.setNotificationId("N-" + order.getOrderId() + "-" + System.currentTimeMillis());
            notif.setType(Notification.TYPE_ORDER_CONFIRM);
            notif.setRecipient(client.getEmail());
            notif.setChannel(Notification.CHANNEL_EMAIL);
            notif.setOrderId(order.getOrderId());
            notif.setSubject("Order Confirmation - " + order.getOrderId());

            // Body uses pipe-delimited format for template substitution in EmailDispatcher
            // Format: symbol|quantity|side|price|reason|amount|settlementDate
            // (see EmailDispatcher.buildEmailBody for the parsing logic)
            StringBuffer body = new StringBuffer();
            body.append(order.getSymbol()).append("|");
            body.append(order.getQuantity()).append("|");
            body.append(order.getSide()).append("|");
            body.append(price).append("|");
            body.append("").append("|");  // reason (empty for confirmations)
            body.append((order.getQuantity() * price) + commission).append("|");
            body.append("");  // settlementDate (filled by settlement gateway)
            notif.setBody(body.toString());

            String notifXml = XmlHelper.marshalNotification(notif);
            MessageQueueHelper.sendMessage(MessageQueueHelper.QUEUE_NOTIFICATIONS, notifXml);

            System.out.println("Sent confirmation notification for " + order.getOrderId());

        } catch (Exception e) {
            // don't fail the order just because notification failed
            System.err.println("ERROR: Failed to send confirmation notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send an order rejection notification.
     */
    private void sendRejectionNotification(TradeOrder order, Client client, String reason) {
        try {
            Notification notif = new Notification();
            notif.setNotificationId("N-" + order.getOrderId() + "-REJ-" + System.currentTimeMillis());
            notif.setType(Notification.TYPE_ORDER_REJECT);
            notif.setRecipient(client.getEmail());
            notif.setChannel(Notification.CHANNEL_EMAIL);
            notif.setOrderId(order.getOrderId());
            notif.setSubject("Order Rejected - " + order.getOrderId());

            // pipe-delimited format: symbol|quantity|side|price|reason
            StringBuffer body = new StringBuffer();
            body.append(order.getSymbol()).append("|");
            body.append(order.getQuantity()).append("|");
            body.append(order.getSide()).append("|");
            body.append(order.getRequestedPrice()).append("|");
            body.append(reason);
            notif.setBody(body.toString());

            String notifXml = XmlHelper.marshalNotification(notif);
            MessageQueueHelper.sendMessage(MessageQueueHelper.QUEUE_NOTIFICATIONS, notifXml);

            System.out.println("Sent rejection notification for " + order.getOrderId());

        } catch (Exception e) {
            System.err.println("ERROR: Failed to send rejection notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send order status update to the trade confirmations queue.
     * Other systems (settlement, audit) consume from this queue.
     */
    private void sendStatusUpdate(TradeOrder order) {
        try {
            String orderXml = XmlHelper.marshalTradeOrder(order);
            MessageQueueHelper.sendMessage(MessageQueueHelper.QUEUE_TRADE_CONFIRMATIONS, orderXml);
            System.out.println("Sent status update to confirmations queue for " + order.getOrderId());
        } catch (Exception e) {
            System.err.println("ERROR: Failed to send status update: " + e.getMessage());
            // this is bad but we can't do much about it
            // the settlement batch will pick up the status from the DB anyway
        }
    }

    /**
     * Stop the listener gracefully.
     * Sets the running flag to false so the polling loop exits
     * on the next iteration.
     */
    public void stop() {
        System.out.println("Stopping OrderMessageListener...");
        this.running = false;
    }

    /**
     * Clean up JMS resources.
     */
    private void cleanup() {
        System.out.println("Cleaning up JMS resources...");
        try {
            if (jmsConsumer != null) jmsConsumer.close();
        } catch (JMSException e) {
            System.err.println("WARN: Error closing consumer: " + e.getMessage());
        }
        try {
            if (jmsSession != null) jmsSession.close();
        } catch (JMSException e) {
            System.err.println("WARN: Error closing session: " + e.getMessage());
        }
        try {
            if (jmsConnection != null) jmsConnection.close();
        } catch (JMSException e) {
            System.err.println("WARN: Error closing connection: " + e.getMessage());
        }
    }
}
