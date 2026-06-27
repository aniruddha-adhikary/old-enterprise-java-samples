package com.bigcorp.settlement.batch;

import com.bigcorp.common.billing.CommissionCalculator;
import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.model.Notification;
import com.bigcorp.common.model.SettlementRecord;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.common.xml.XmlHelper;
import com.bigcorp.settlement.dao.SettlementDAO;
import com.bigcorp.settlement.generator.SettlementFileGenerator;
import com.bigcorp.settlement.sftp.SftpUploader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * End-of-day batch processor for settlement.
 * 
 * Takes filled orders, creates settlement records, generates files,
 * and uploads to the clearinghouse.
 * 
 * In production this runs at 6 PM EST via cron. The Timer-based 
 * scheduling in BatchScheduler is just for testing/demo purposes.
 * 
 * @author Dave (settlements team)
 * @since 1.1
 */
public class BatchProcessor {

    private SettlementDAO dao;
    private SettlementFileGenerator fileGenerator;
    private SftpUploader uploader;

    // batch sequence counter (resets daily in production, but we don't do that here)
    private static int batchSequence = 1;

    // JIRA-2501: commission rate now derived from client tier
    // via CommissionCalculator (was hardcoded as 0.02)

    // partial batch mode flag (requested in JIRA-2890 but never fully implemented)
    private boolean partialBatchMode = false;
    // private int partialBatchSize = 100; // unused

    public BatchProcessor() {
        this.dao = new SettlementDAO();
        this.fileGenerator = new SettlementFileGenerator();
        this.uploader = new SftpUploader();
    }

    /**
     * Process the end-of-day settlement batch.
     * 
     * Steps:
     *   1. Find all filled orders
     *   2. Create settlement records
     *   3. Generate XML and flat files
     *   4. Upload files to clearinghouse
     *   5. Send notifications
     */
    public void processBatch() {
        System.out.println("========================================");
        System.out.println("Settlement Batch Processing - START");
        System.out.println("Time: " + new Date());
        System.out.println("========================================");

        try {
            // Step 1: Find filled orders
            List filledOrders = dao.findFilledOrders();
            System.out.println("Found " + filledOrders.size() + " filled orders to settle");

            if (filledOrders.size() == 0) {
                System.out.println("No orders to process. Batch complete.");
                return;
            }

            // Generate batch ID
            String batchId = generateBatchId();
            System.out.println("Batch ID: " + batchId);

            // Step 2: Create settlement records
            List settlementRecords = new ArrayList();
            for (int i = 0; i < filledOrders.size(); i++) {
                TradeOrder order = (TradeOrder) filledOrders.get(i);

                // partial batch mode - was supposed to limit batch size
                // but the logic was never completed (JIRA-2890)
                if (partialBatchMode) {
                    // TODO: implement partial batch logic
                    // if (settlementRecords.size() >= partialBatchSize) break;
                }

                SettlementRecord record = createSettlementRecord(order, batchId);
                dao.saveSettlementRecord(record);
                settlementRecords.add(record);

                // Update order status to SETTLED
                dao.updateOrderStatus(order.getOrderId(), TradeOrder.STATUS_SETTLED);
            }

            System.out.println("Created " + settlementRecords.size() + " settlement records");

            // Step 3: Generate files
            String xmlFile = fileGenerator.generateXmlFile(settlementRecords, batchId);
            String flatFile = fileGenerator.generateFlatFile(settlementRecords, batchId);

            // Step 4: Upload files
            if (xmlFile != null) {
                uploader.upload(xmlFile, null);
                updateRecordStatus(settlementRecords, SettlementRecord.STATUS_UPLOADED);
            }
            if (flatFile != null) {
                uploader.upload(flatFile, null);
            }

            // Step 5: Send notifications
            for (int i = 0; i < settlementRecords.size(); i++) {
                SettlementRecord rec = (SettlementRecord) settlementRecords.get(i);
                sendSettlementNotification(rec);
            }

            System.out.println("========================================");
            System.out.println("Settlement Batch Processing - COMPLETE");
            System.out.println("Processed: " + settlementRecords.size() + " records");
            System.out.println("Batch ID: " + batchId);
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("ERROR: Batch processing failed: " + e.getMessage());
            e.printStackTrace();
            // TODO: should we roll back? Currently partial batches can happen 
            // if this fails midway. Dave says "we'll handle it manually if it happens"
        }
    }

    /**
     * Create a settlement record from a filled order.
     */
    private SettlementRecord createSettlementRecord(TradeOrder order, String batchId) {
        SettlementRecord record = new SettlementRecord();

        // generate record ID
        String recordId = "SR-" + System.currentTimeMillis() + "-" + order.getOrderId().hashCode();
        record.setRecordId(recordId);
        record.setOrderId(order.getOrderId());
        record.setClientId(order.getClientId());
        record.setSymbol(order.getSymbol());
        record.setQuantity(order.getQuantity());
        record.setSide(order.getSide());

        // calculate amount and commission (tier-based via CommissionCalculator)
        double amount = order.getQuantity() * order.getPrice();
        String clientTier = lookupClientTier(order.getClientId());
        double commission = CommissionCalculator.calculate(amount, clientTier);
        record.setAmount(amount);
        record.setCommission(commission);

        // trade date is the order date
        record.setTradeDate(order.getOrderDate());

        // settlement date = trade date + 3 days (T+3)
        // NOTE: This does NOT actually skip weekends. Known bug documented in JIRA-2890.
        // The clearinghouse doesn't seem to care because they recalculate on their end anyway.
        // "We'll fix it when we have time" — Dave, 2001
        Date settlementDate = calculateSettlementDate(order.getOrderDate());
        record.setSettlementDate(settlementDate);

        record.setStatus(SettlementRecord.STATUS_PENDING);
        record.setBatchId(batchId);

        return record;
    }

    /**
     * Calculate T+3 settlement date.
     * Does NOT skip weekends or holidays. See JIRA-2890.
     */
    private Date calculateSettlementDate(Date tradeDate) {
        if (tradeDate == null) {
            tradeDate = new Date();
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(tradeDate);
        cal.add(Calendar.DAY_OF_MONTH, 3);
        // TODO: skip weekends and holidays (JIRA-2890)
        // The holiday calendar is supposed to come from a file that compliance maintains
        // but they never gave it to us in a parseable format
        return cal.getTime();
    }

    /**
     * Generate a batch ID in format: BATCH-yyyyMMdd-NNN
     */
    private String generateBatchId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(new Date());
        String seqStr = String.valueOf(batchSequence++);
        // pad sequence to 3 digits
        while (seqStr.length() < 3) {
            seqStr = "0" + seqStr;
        }
        return "BATCH-" + dateStr + "-" + seqStr;
    }

    /**
     * Update status on all records in the batch.
     */
    private void updateRecordStatus(List records, String status) {
        for (int i = 0; i < records.size(); i++) {
            SettlementRecord rec = (SettlementRecord) records.get(i);
            dao.updateSettlementStatus(rec.getRecordId(), status);
            rec.setStatus(status);
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
     * Send a settlement notification via the message queue.
     */
    private void sendSettlementNotification(SettlementRecord record) {
        try {
            Notification notif = new Notification();
            notif.setNotificationId("NOTIF-" + System.currentTimeMillis() + "-" + record.getRecordId().hashCode());
            notif.setType(Notification.TYPE_SETTLEMENT);
            notif.setRecipient(record.getClientId()); // client ID as placeholder; real email looked up by notification service
            notif.setSubject("Settlement Confirmation - Order " + record.getOrderId());
            notif.setBody("Your order " + record.getOrderId() + " for " + record.getQuantity() 
                    + " " + record.getSymbol() + " has been settled. "
                    + "Amount: $" + record.getAmount() + ", Commission: $" + record.getCommission()
                    + ". Settlement date: " + record.getSettlementDate());
            notif.setChannel(Notification.CHANNEL_EMAIL);
            notif.setOrderId(record.getOrderId());

            String xml = XmlHelper.marshalNotification(notif);
            MessageQueueHelper.sendMessage(MessageQueueHelper.QUEUE_NOTIFICATIONS, xml);

        } catch (Exception e) {
            // notification failures should not stop the batch
            System.err.println("WARN: Failed to send notification for record " + record.getRecordId() + ": " + e.getMessage());
        }
    }
}
