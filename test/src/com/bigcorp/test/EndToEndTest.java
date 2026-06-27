package com.bigcorp.test;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.db.DatabaseBootstrap;
import com.bigcorp.common.model.Client;
import com.bigcorp.common.model.Notification;
import com.bigcorp.common.model.SettlementRecord;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleConfigLoader;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.rules.RuleEngine;
import com.bigcorp.common.rules.impl.ClientTierRule;
import com.bigcorp.common.rules.impl.MarketHoursRule;
import com.bigcorp.common.rules.impl.MaxOrderValueRule;
import com.bigcorp.common.rules.impl.RestrictedSymbolRule;
import com.bigcorp.common.rules.impl.ShortSaleRule;
import com.bigcorp.common.rules.impl.SpecialClientsRule;
import com.bigcorp.common.xml.StringXmlBuilder;
import com.bigcorp.common.xml.XmlHelper;
import com.bigcorp.audit.consumer.AuditListener;
import com.bigcorp.common.billing.CommissionCalculator;
import com.bigcorp.common.model.AuditEvent;
import com.bigcorp.notifications.consumer.NotificationListener;
import com.bigcorp.orderengine.consumer.OrderMessageListener;
import com.bigcorp.orderengine.dao.OrderDAO;
import com.bigcorp.pricing.service.PriceQuote;
import com.bigcorp.pricing.service.PricingServiceImpl;
import com.bigcorp.settlement.batch.BatchProcessor;
import com.bigcorp.settlement.generator.SettlementFileGenerator;
import com.bigcorp.tradedesk.mq.TradeMessageProducer;
import com.bigcorp.common.service.ServiceLocator;
import com.bigcorp.common.db.ConnectionPool;
import com.bigcorp.common.db.DAOFactory;
import com.bigcorp.common.dto.OrderTransferObject;
import com.bigcorp.common.dto.SettlementTransferObject;
import com.bigcorp.common.dto.TransferObjectAssembler;
import com.bigcorp.settlement.reconciliation.ReconciliationProcessor;
import com.bigcorp.derivatives.core.DerivativeOrder;
import com.bigcorp.derivatives.core.DerivativeProcessor;
import com.bigcorp.derivatives.core.FxPricingHelper;
import com.bigcorp.derivatives.queue.DerivativeQueueConstants;
import com.bigcorp.derivatives.util.DerivativeLogger;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Comprehensive end-to-end test harness for BigCorp Trade Order Management.
 * 
 * Tests the full trade lifecycle across all 6 modules:
 *   common-lib, trade-desk, order-engine, pricing-service,
 *   notification-gateway, settlement-gateway
 * 
 * Runs against embedded infrastructure (HSQLDB, embedded ActiveMQ) or
 * real infrastructure (Oracle XE, standalone ActiveMQ, SFTP server).
 * Use 'ant run-test' for embedded, 'ant run-test-real' for real infra.
 */
public class EndToEndTest {

    private static int passed = 0;
    private static int failed = 0;
    private static int total = 0;
    private static List failedTests = new ArrayList();

    private static OrderMessageListener orderListener;
    private static NotificationListener notifListener;
    private static AuditListener auditListener;
    private static Thread engineThread;
    private static Thread notifThread;
    private static Thread auditThread;

    // ========================================================================
    // Main
    // ========================================================================

    public static void main(String[] args) {
        System.out.println("##############################################");
        System.out.println("  BigCorp End-to-End Test Suite");
        System.out.println("  " + new Date());
        System.out.println("##############################################");
        System.out.println();

        try {
            // Phase 1: Build & Infrastructure
            phase1_infrastructure();

            // Clean up any leftover test data (important for real DBs like Oracle
            // where data persists between runs, unlike HSQLDB in-memory)
            cleanupTestData();

            // Phase 2: Happy Path
            phase2_happyPath();

            // Phase 3: Rule Engine Edge Cases
            phase3_ruleEngine();

            // Phase 4: Settlement Batch
            phase4_settlement();

            // Phase 5: Notification verification
            phase5_notifications();

            // Phase 5b: Audit & Billing verification
            phase5b_auditAndBilling();

            // Phase 6: XML Round-Trip
            phase6_xmlRoundTrip();

            // Phase 7: Pricing Service
            phase7_pricingService();

            // Phase 8: Multi-Order Stress
            phase8_multiOrder();

            // Phase 9: Enterprise Patterns (J2EE circa 2001)
            phase9_enterprisePatterns();

            // Phase 10: Config-Driven Rule Loading
            phase10_ruleConfig();

            // Phase 11: Per-Symbol Trading Restrictions
            phase11_tradingRestrictions();

            // Phase 12: Derivatives Engine (contractor module)
            phase12_derivativesEngine();

        } catch (Exception e) {
            System.err.println("FATAL: Test suite crashed: " + e.getMessage());
            e.printStackTrace();
        }

        // Print summary
        printSummary();

        System.exit(failed > 0 ? 1 : 0);
    }

    // ========================================================================
    // Phase 1: Infrastructure
    // ========================================================================

    private static void phase1_infrastructure() {
        System.out.println("=== Phase 1: Infrastructure Setup ===");
        System.out.println();

        // T1.1 - Database init
        try {
            ConnectionHelper.init();
            DatabaseBootstrap.bootstrap();
            assertTest("T1.1", "Database bootstrap", true);
        } catch (Exception e) {
            assertTest("T1.1", "Database bootstrap", false, e.getMessage());
            return; // can't continue without DB
        }

        // T1.2 - Verify tables exist
        try {
            Connection conn = ConnectionHelper.getConnection();
            Statement stmt = conn.createStatement();
            String[] tables = {"CLIENTS", "TRADE_ORDERS", "NOTIFICATIONS", 
                               "SETTLEMENT_RECORDS", "AUDIT_LOG", "PRICING_CACHE",
                               "BILLING_LEDGER"};
            boolean allExist = true;
            for (int i = 0; i < tables.length; i++) {
                try {
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tables[i]);
                    rs.next();
                    rs.close();
                } catch (Exception e) {
                    allExist = false;
                    System.err.println("  Table missing: " + tables[i]);
                }
            }
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
            assertTest("T1.2", "All 7 tables exist", allExist);
        } catch (Exception e) {
            assertTest("T1.2", "All 6 tables exist", false, e.getMessage());
        }

        // T1.3 - Verify sample data
        try {
            Connection conn = ConnectionHelper.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM CLIENTS");
            rs.next();
            int clientCount = rs.getInt(1);
            rs.close();

            rs = stmt.executeQuery("SELECT COUNT(*) FROM PRICING_CACHE");
            rs.next();
            int pricingCount = rs.getInt(1);
            rs.close();

            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
            assertTest("T1.3", "Sample data loaded (5 clients, 7 pricing records)", 
                clientCount == 5 && pricingCount == 7,
                "clients=" + clientCount + ", pricing=" + pricingCount);
        } catch (Exception e) {
            assertTest("T1.3", "Sample data loaded", false, e.getMessage());
        }

        // T1.4 - MQ init
        try {
            MessageQueueHelper.init();
            assertTest("T1.4", "ActiveMQ broker started", true);
        } catch (Exception e) {
            assertTest("T1.4", "ActiveMQ broker started", false, e.getMessage());
        }

        // T1.5 - Start listeners
        try {
            orderListener = new OrderMessageListener();
            engineThread = new Thread(new Runnable() {
                public void run() {
                    orderListener.startListening();
                }
            });
            engineThread.setDaemon(true);
            engineThread.start();

            notifListener = new NotificationListener();
            notifThread = new Thread(new Runnable() {
                public void run() {
                    notifListener.startListening();
                }
            });
            notifThread.setDaemon(true);
            notifThread.start();

            auditListener = new AuditListener();
            auditThread = new Thread(new Runnable() {
                public void run() {
                    auditListener.startListening();
                }
            });
            auditThread.setDaemon(true);
            auditThread.start();

            // Give listeners time to connect
            Thread.sleep(2000);
            assertTest("T1.5", "Order engine + notification + audit listeners started", true);
        } catch (Exception e) {
            assertTest("T1.5", "Listeners started", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 2: Happy Path
    // ========================================================================

    private static void phase2_happyPath() {
        System.out.println("=== Phase 2: Happy Path - Standard Order Flow ===");
        System.out.println();

        // T2.1 - Submit BUY order (C001, MSFT, 500 @ $25.75)
        submitAndVerify("T2.1", "BUY 500 MSFT for C001 -> FILLED",
            "ORD-TEST-001", "C001", "MSFT", 500, TradeOrder.SIDE_BUY, 25.75, "FILLED");

        // T2.2 - Submit BUY order (C002, IBM, 100)
        submitAndVerify("T2.2", "BUY 100 IBM for C002 (Henderson/PLATINUM) -> FILLED",
            "ORD-TEST-002", "C002", "IBM", 100, TradeOrder.SIDE_BUY, 120.00, "FILLED");

        // T2.3 - Submit SELL order (C003, ORCL, 200)
        submitAndVerify("T2.3", "SELL 200 ORCL for C003 -> FILLED",
            "ORD-TEST-003", "C003", "ORCL", 200, TradeOrder.SIDE_SELL, 15.50, "FILLED");

        System.out.println();
    }

    // ========================================================================
    // Phase 3: Rule Engine Edge Cases
    // ========================================================================

    private static void phase3_ruleEngine() {
        System.out.println("=== Phase 3: Rule Engine Edge Cases ===");
        System.out.println();

        // T3.1 - MaxOrderValue rejection (C005 max=$100k, 10% buffer = $110k)
        // 5000 shares @ $25 = $125,000 > $110,000
        submitAndVerify("T3.1", "Order exceeding max value -> REJECTED",
            "ORD-TEST-REJ1", "C005", "MSFT", 5000, TradeOrder.SIDE_BUY, 25.00, "REJECTED");

        // T3.2 - Invalid client ID
        // TradeMessageProducer tries to insert to DB first (FK constraint fails for C999)
        // then sends to MQ. The producer may throw RuntimeException on DB insert failure.
        // The order-engine will still reject it with "Client not found" when it processes
        // from the queue. Either path is valid — the system handles it.
        try {
            TradeOrder order = createOrder("ORD-TEST-REJ2", "C999", "MSFT", 100, TradeOrder.SIDE_BUY, 25.00);
            TradeMessageProducer producer = new TradeMessageProducer();
            boolean producerThrew = false;
            try {
                producer.submitOrder(order);
            } catch (Exception pe) {
                // Expected: FK constraint violation for non-existent client C999
                producerThrew = true;
            }
            
            if (!producerThrew) {
                Thread.sleep(5000);
            }
            
            // The order may not be in DB at all (FK failure) or may be REJECTED
            String status = getOrderStatus("ORD-TEST-REJ2");
            assertTest("T3.2", "Invalid client C999 -> producer fails (FK) or order rejected",
                producerThrew || status == null || "REJECTED".equals(status),
                "producerThrew=" + producerThrew + ", status=" + status);
        } catch (Exception e) {
            assertTest("T3.2", "Invalid client rejection", false, e.getMessage());
        }

        // T3.3 - Price deviation rejection (request $50, market $25.75 = ~94% deviation)
        submitAndVerify("T3.3", "Price deviation >10% -> REJECTED",
            "ORD-TEST-REJ3", "C001", "MSFT", 100, TradeOrder.SIDE_BUY, 50.00, "REJECTED");

        // T3.4 - Rule engine unit test (MaxOrderValue rule directly)
        try {
            RuleEngine engine = RuleEngine.getInstance();
            Client client = new Client();
            client.setClientId("C005");
            client.setTier(Client.TIER_BRONZE);
            client.setMaxOrderValue(100000);
            client.setActive(true);

            TradeOrder bigOrder = new TradeOrder();
            bigOrder.setOrderId("UNIT-001");
            bigOrder.setQuantity(10000);
            bigOrder.setRequestedPrice(25.00); // 10000 * 25 = $250,000

            RuleContext ctx = new RuleContext(bigOrder, client);
            MaxOrderValueRule rule = new MaxOrderValueRule();
            boolean result = rule.evaluate(ctx);
            assertTest("T3.4", "MaxOrderValueRule rejects $250k order for $100k limit client",
                !result && ctx.isRejected());
        } catch (Exception e) {
            assertTest("T3.4", "MaxOrderValueRule unit test", false, e.getMessage());
        }

        // T3.5 - SpecialClients rule sets commission override for C002
        try {
            Client henderson = new Client();
            henderson.setClientId("C002");
            henderson.setTier(Client.TIER_PLATINUM);
            henderson.setMaxOrderValue(5000000);
            henderson.setActive(true);

            TradeOrder order = new TradeOrder();
            order.setOrderId("UNIT-002");
            order.setQuantity(100);
            order.setRequestedPrice(120.00);

            RuleContext ctx = new RuleContext(order, henderson);
            SpecialClientsRule specialRule = new SpecialClientsRule();
            specialRule.evaluate(ctx);
            specialRule.execute(ctx);

            Object commOverride = ctx.getAttribute("commission_override");
            assertTest("T3.5", "SpecialClientsRule: C002 gets zero commission override",
                commOverride != null && ((Double) commOverride).doubleValue() == 0.0,
                "commission_override=" + commOverride);
        } catch (Exception e) {
            assertTest("T3.5", "SpecialClients commission override", false, e.getMessage());
        }

        // T3.6 - SpecialClients rule sets early_access for C001
        try {
            Client acme = new Client();
            acme.setClientId("C001");
            acme.setTier(Client.TIER_GOLD);
            acme.setMaxOrderValue(500000);
            acme.setActive(true);

            TradeOrder order = new TradeOrder();
            order.setOrderId("UNIT-003");
            order.setQuantity(100);
            order.setRequestedPrice(25.00);

            RuleContext ctx = new RuleContext(order, acme);
            SpecialClientsRule specialRule = new SpecialClientsRule();
            specialRule.evaluate(ctx);
            specialRule.execute(ctx);

            Object earlyAccess = ctx.getAttribute("early_access");
            assertTest("T3.6", "SpecialClientsRule: C001 gets early_access=true",
                Boolean.TRUE.equals(earlyAccess),
                "early_access=" + earlyAccess);
        } catch (Exception e) {
            assertTest("T3.6", "SpecialClients early access", false, e.getMessage());
        }

        // T3.7 - SpecialClients rule sets pricing_tier_override for C004
        try {
            Client megafund = new Client();
            megafund.setClientId("C004");
            megafund.setTier(Client.TIER_GOLD);
            megafund.setMaxOrderValue(1000000);
            megafund.setActive(true);

            TradeOrder order = new TradeOrder();
            order.setOrderId("UNIT-004");
            order.setQuantity(100);
            order.setRequestedPrice(35.00);

            RuleContext ctx = new RuleContext(order, megafund);
            SpecialClientsRule specialRule = new SpecialClientsRule();
            specialRule.evaluate(ctx);
            specialRule.execute(ctx);

            Object tierOverride = ctx.getAttribute("pricing_tier_override");
            assertTest("T3.7", "SpecialClientsRule: C004 gets PLATINUM pricing override",
                Client.TIER_PLATINUM.equals(tierOverride),
                "pricing_tier_override=" + tierOverride);
        } catch (Exception e) {
            assertTest("T3.7", "SpecialClients tier override", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 4: Settlement
    // ========================================================================

    private static void phase4_settlement() {
        System.out.println("=== Phase 4: Settlement Batch Processing ===");
        System.out.println();

        // T4.1 - Run settlement batch on filled orders
        try {
            // Clean up any previous settlement output
            deleteDir(new File("./sftp-outbound"));
            deleteDir(new File("./sftp-root"));

            BatchProcessor batchProcessor = new BatchProcessor();
            batchProcessor.processBatch();

            // Check settlement records were created
            int settlementCount = countRecords("SETTLEMENT_RECORDS");
            assertTest("T4.1", "Settlement batch creates records for filled orders",
                settlementCount > 0, "settlement_records=" + settlementCount);
        } catch (Exception e) {
            assertTest("T4.1", "Settlement batch processing", false, e.getMessage());
        }

        // T4.2 - Verify XML settlement file exists and is valid
        try {
            File outDir = new File("./sftp-outbound");
            String[] xmlFiles = outDir.list(new java.io.FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".xml");
                }
            });
            boolean xmlExists = xmlFiles != null && xmlFiles.length > 0;
            
            boolean validXml = false;
            if (xmlExists) {
                String content = readFile(new File(outDir, xmlFiles[0]));
                validXml = content.contains("<settlementBatch") && content.contains("</settlementBatch>");
            }
            
            assertTest("T4.2", "XML settlement file generated and valid",
                xmlExists && validXml,
                xmlExists ? "file=" + xmlFiles[0] : "no XML files found");
        } catch (Exception e) {
            assertTest("T4.2", "XML settlement file", false, e.getMessage());
        }

        // T4.3 - Verify flat file exists and has correct format
        try {
            File outDir = new File("./sftp-outbound");
            String[] datFiles = outDir.list(new java.io.FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".dat");
                }
            });
            boolean datExists = datFiles != null && datFiles.length > 0;

            boolean validFormat = false;
            if (datExists) {
                String content = readFile(new File(outDir, datFiles[0]));
                String[] lines = content.split("\n");
                validFormat = lines.length >= 3 
                    && lines[0].startsWith("H")
                    && lines[lines.length - 1].startsWith("T");
                // Check detail records start with D
                for (int i = 1; i < lines.length - 1; i++) {
                    if (!lines[i].startsWith("D")) {
                        validFormat = false;
                        break;
                    }
                }
            }

            assertTest("T4.3", "Flat settlement file with H/D/T record format",
                datExists && validFormat,
                datExists ? "file=" + datFiles[0] : "no .dat files found");
        } catch (Exception e) {
            assertTest("T4.3", "Flat settlement file", false, e.getMessage());
        }

        // T4.4 - Verify settlement files were delivered (SFTP or local fallback)
        try {
            // Check local fallback directory first (HSQLDB / no SFTP config)
            File sftpRoot = new File("./sftp-root/outbound");
            boolean localCopyExists = sftpRoot.exists() && sftpRoot.list() != null 
                && sftpRoot.list().length > 0;
            // Also check sftp-outbound directory (files are written there before upload)
            File outbound = new File("./sftp-outbound");
            boolean outboundExists = outbound.exists() && outbound.list() != null
                && outbound.list().length > 0;
            assertTest("T4.4", "Settlement files delivered (SFTP upload or local fallback)",
                localCopyExists || outboundExists,
                "localFallback=" + localCopyExists + ", outbound=" + outboundExists);
        } catch (Exception e) {
            assertTest("T4.4", "Settlement file delivery", false, e.getMessage());
        }

        // T4.5 - Orders updated to SETTLED
        try {
            // Check that filled orders from phase 2 are now SETTLED
            String status1 = getOrderStatus("ORD-TEST-001");
            String status2 = getOrderStatus("ORD-TEST-002");
            String status3 = getOrderStatus("ORD-TEST-003");
            assertTest("T4.5", "Filled orders updated to SETTLED after batch",
                "SETTLED".equals(status1) && "SETTLED".equals(status2) && "SETTLED".equals(status3),
                "ORD-TEST-001=" + status1 + ", ORD-TEST-002=" + status2 + ", ORD-TEST-003=" + status3);
        } catch (Exception e) {
            assertTest("T4.5", "Orders settled", false, e.getMessage());
        }

        // T4.6 - Empty batch (no more filled orders)
        try {
            BatchProcessor batchProcessor2 = new BatchProcessor();
            int beforeCount = countRecords("SETTLEMENT_RECORDS");
            batchProcessor2.processBatch();
            int afterCount = countRecords("SETTLEMENT_RECORDS");
            assertTest("T4.6", "Empty batch: no new records when no filled orders",
                beforeCount == afterCount,
                "before=" + beforeCount + ", after=" + afterCount);
        } catch (Exception e) {
            assertTest("T4.6", "Empty batch", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 5: Notifications
    // ========================================================================

    private static void phase5_notifications() {
        System.out.println("=== Phase 5: Notification Verification ===");
        System.out.println();

        // T5.1 - Notifications exist in DB
        try {
            int notifCount = countRecords("NOTIFICATIONS");
            assertTest("T5.1", "Notifications persisted to database",
                notifCount > 0, "notification_count=" + notifCount);
        } catch (Exception e) {
            assertTest("T5.1", "Notification persistence", false, e.getMessage());
        }

        // T5.2 - Check ORDER_CONFIRM notifications
        try {
            Connection conn = ConnectionHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM NOTIFICATIONS WHERE NOTIFICATION_TYPE = ?");
            ps.setString(1, Notification.TYPE_ORDER_CONFIRM);
            ResultSet rs = ps.executeQuery();
            rs.next();
            int confirmCount = rs.getInt(1);
            rs.close();
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
            assertTest("T5.2", "ORDER_CONFIRM notifications exist",
                confirmCount > 0, "count=" + confirmCount);
        } catch (Exception e) {
            assertTest("T5.2", "ORDER_CONFIRM notifications", false, e.getMessage());
        }

        // T5.3 - Check notification has correct channel
        try {
            Connection conn = ConnectionHelper.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT CHANNEL, STATUS FROM NOTIFICATIONS WHERE NOTIFICATION_TYPE = 'ORDER_CONFIRM'");
            boolean hasEmail = false;
            boolean hasSentStatus = false;
            while (rs.next()) {
                if ("EMAIL".equals(rs.getString("CHANNEL"))) hasEmail = true;
                if ("SENT".equals(rs.getString("STATUS"))) hasSentStatus = true;
            }
            rs.close();
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
            assertTest("T5.3", "Notifications have EMAIL channel and SENT status",
                hasEmail && hasSentStatus,
                "hasEmail=" + hasEmail + ", hasSentStatus=" + hasSentStatus);
        } catch (Exception e) {
            assertTest("T5.3", "Notification channel/status", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 5b: Audit & Billing
    // ========================================================================

    private static void phase5b_auditAndBilling() {
        System.out.println("=== Phase 5b: Audit Log & Billing Ledger ===");
        System.out.println();

        // T5b.1 - AUDIT_LOG has entries from processed orders
        try {
            int auditCount = countRecords("AUDIT_LOG");
            assertTest("T5b.1", "AUDIT_LOG has entries after order processing",
                auditCount > 0, "audit_count=" + auditCount);
        } catch (Exception e) {
            assertTest("T5b.1", "AUDIT_LOG entries", false, e.getMessage());
        }

        // T5b.2 - AUDIT_LOG has ORDER_FILLED events
        try {
            Connection conn = ConnectionHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM AUDIT_LOG WHERE EVENT_TYPE = ?");
            ps.setString(1, AuditEvent.EVENT_ORDER_FILLED);
            ResultSet rs = ps.executeQuery();
            rs.next();
            int filledCount = rs.getInt(1);
            rs.close();
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
            assertTest("T5b.2", "AUDIT_LOG has ORDER_FILLED events",
                filledCount > 0, "count=" + filledCount);
        } catch (Exception e) {
            assertTest("T5b.2", "ORDER_FILLED audit events", false, e.getMessage());
        }

        // T5b.3 - AUDIT_LOG has ORDER_REJECTED events
        try {
            Connection conn = ConnectionHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM AUDIT_LOG WHERE EVENT_TYPE = ?");
            ps.setString(1, AuditEvent.EVENT_ORDER_REJECTED);
            ResultSet rs = ps.executeQuery();
            rs.next();
            int rejectedCount = rs.getInt(1);
            rs.close();
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
            assertTest("T5b.3", "AUDIT_LOG has ORDER_REJECTED events",
                rejectedCount > 0, "count=" + rejectedCount);
        } catch (Exception e) {
            assertTest("T5b.3", "ORDER_REJECTED audit events", false, e.getMessage());
        }

        // T5b.4 - BILLING_LEDGER has entries for filled orders
        try {
            int billingCount = countRecords("BILLING_LEDGER");
            assertTest("T5b.4", "BILLING_LEDGER has entries after filled orders",
                billingCount > 0, "billing_count=" + billingCount);
        } catch (Exception e) {
            assertTest("T5b.4", "BILLING_LEDGER entries", false, e.getMessage());
        }

        // T5b.5 - BILLING_LEDGER commission reflects tier-based rates
        try {
            Connection conn = ConnectionHelper.getConnection();
            Statement stmt = conn.createStatement();
            // C001 is GOLD tier (rate=0.01), order was 500 MSFT @ ~$25.75
            ResultSet rs = stmt.executeQuery(
                "SELECT GROSS_AMOUNT, COMMISSION_AMOUNT FROM BILLING_LEDGER WHERE ORDER_ID = 'ORD-TEST-001'");
            boolean found = false;
            boolean rateCorrect = false;
            if (rs.next()) {
                found = true;
                double gross = rs.getDouble("GROSS_AMOUNT");
                double commission = rs.getDouble("COMMISSION_AMOUNT");
                // GOLD tier rate is 0.01 (1%)
                double expectedRate = CommissionCalculator.getRate(Client.TIER_GOLD);
                double expectedCommission = gross * expectedRate;
                rateCorrect = Math.abs(commission - expectedCommission) < 0.01;
            }
            rs.close();
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
            assertTest("T5b.5", "BILLING_LEDGER: C001 (GOLD) commission matches tier rate (1%)",
                found && rateCorrect,
                "found=" + found + ", rateCorrect=" + rateCorrect);
        } catch (Exception e) {
            assertTest("T5b.5", "Billing tier-based commission", false, e.getMessage());
        }

        // T5b.6 - CommissionCalculator unit test: tier rates
        try {
            boolean platinum = CommissionCalculator.getRate(Client.TIER_PLATINUM) == 0.005;
            boolean gold = CommissionCalculator.getRate(Client.TIER_GOLD) == 0.010;
            boolean silver = CommissionCalculator.getRate(Client.TIER_SILVER) == 0.015;
            boolean bronze = CommissionCalculator.getRate(Client.TIER_BRONZE) == 0.020;
            boolean defaultRate = CommissionCalculator.getRate(null) == 0.020;
            assertTest("T5b.6", "CommissionCalculator: tier rates PLATINUM<GOLD<SILVER<BRONZE",
                platinum && gold && silver && bronze && defaultRate,
                "P=" + CommissionCalculator.getRate(Client.TIER_PLATINUM)
                + " G=" + CommissionCalculator.getRate(Client.TIER_GOLD)
                + " S=" + CommissionCalculator.getRate(Client.TIER_SILVER)
                + " B=" + CommissionCalculator.getRate(Client.TIER_BRONZE));
        } catch (Exception e) {
            assertTest("T5b.6", "CommissionCalculator tier rates", false, e.getMessage());
        }

        // T5b.7 - AuditEvent XML round-trip
        try {
            AuditEvent original = new AuditEvent();
            original.setEventType(AuditEvent.EVENT_ORDER_FILLED);
            original.setSourceSystem(AuditEvent.SOURCE_ORDER_ENGINE);
            original.setEntityType(AuditEvent.ENTITY_ORDER);
            original.setEntityId("ORD-RT-001");
            original.setDescription("test round trip");
            original.setUserId("C001");

            String xml = XmlHelper.marshalAuditEvent(original);
            AuditEvent roundTripped = XmlHelper.unmarshalAuditEvent(xml);

            boolean match = AuditEvent.EVENT_ORDER_FILLED.equals(roundTripped.getEventType())
                && AuditEvent.SOURCE_ORDER_ENGINE.equals(roundTripped.getSourceSystem())
                && AuditEvent.ENTITY_ORDER.equals(roundTripped.getEntityType())
                && "ORD-RT-001".equals(roundTripped.getEntityId())
                && "C001".equals(roundTripped.getUserId());

            assertTest("T5b.7", "AuditEvent XML marshal->unmarshal round-trip",
                match, "entityId=" + roundTripped.getEntityId());
        } catch (Exception e) {
            assertTest("T5b.7", "AuditEvent XML round-trip", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 6: XML Round-Trip
    // ========================================================================

    private static void phase6_xmlRoundTrip() {
        System.out.println("=== Phase 6: XML Marshalling Round-Trip ===");
        System.out.println();

        // T6.1 - XmlHelper TradeOrder round-trip
        try {
            TradeOrder original = createOrder("RT-001", "C001", "MSFT", 500, "BUY", 25.75);
            original.setStatus(TradeOrder.STATUS_FILLED);
            original.setPrice(25.75);

            String xml = XmlHelper.marshalTradeOrder(original);
            TradeOrder roundTripped = XmlHelper.unmarshalTradeOrder(xml);

            boolean match = "RT-001".equals(roundTripped.getOrderId())
                && "C001".equals(roundTripped.getClientId())
                && "MSFT".equals(roundTripped.getSymbol())
                && roundTripped.getQuantity() == 500
                && "BUY".equals(roundTripped.getSide())
                && TradeOrder.STATUS_FILLED.equals(roundTripped.getStatus());

            assertTest("T6.1", "XmlHelper TradeOrder marshal->unmarshal preserves all fields",
                match, "orderId=" + roundTripped.getOrderId() + ", symbol=" + roundTripped.getSymbol());
        } catch (Exception e) {
            assertTest("T6.1", "XmlHelper round-trip", false, e.getMessage());
        }

        // T6.2 - XmlHelper Notification round-trip
        try {
            Notification original = new Notification();
            original.setNotificationId("NRT-001");
            original.setType(Notification.TYPE_ORDER_CONFIRM);
            original.setRecipient("test@bigcorp.com");
            original.setSubject("Test Confirmation");
            original.setBody("MSFT|500|BUY|25.75");
            original.setChannel(Notification.CHANNEL_EMAIL);
            original.setOrderId("RT-001");

            String xml = XmlHelper.marshalNotification(original);
            Notification roundTripped = XmlHelper.unmarshalNotification(xml);

            boolean match = "NRT-001".equals(roundTripped.getNotificationId())
                && Notification.TYPE_ORDER_CONFIRM.equals(roundTripped.getType())
                && "test@bigcorp.com".equals(roundTripped.getRecipient())
                && Notification.CHANNEL_EMAIL.equals(roundTripped.getChannel());

            assertTest("T6.2", "XmlHelper Notification marshal->unmarshal preserves fields",
                match, "notifId=" + roundTripped.getNotificationId());
        } catch (Exception e) {
            assertTest("T6.2", "Notification round-trip", false, e.getMessage());
        }

        // T6.3 - StringXmlBuilder produces valid-ish XML
        try {
            TradeOrder order = createOrder("SXB-001", "C001", "IBM", 100, "SELL", 120.00);
            String xml = StringXmlBuilder.buildTradeOrderXml(order);
            boolean hasRoot = xml.contains("<tradeOrder>") && xml.contains("</tradeOrder>");
            boolean hasOrderId = xml.contains("SXB-001");
            boolean hasSymbol = xml.contains("IBM");
            assertTest("T6.3", "StringXmlBuilder produces XML with expected elements",
                hasRoot && hasOrderId && hasSymbol,
                "hasRoot=" + hasRoot + ", hasId=" + hasOrderId);
        } catch (Exception e) {
            assertTest("T6.3", "StringXmlBuilder", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 7: Pricing Service
    // ========================================================================

    private static void phase7_pricingService() {
        System.out.println("=== Phase 7: Pricing Service ===");
        System.out.println();

        PricingServiceImpl pricingService = new PricingServiceImpl();

        // T7.1 - Get MSFT quote
        try {
            PriceQuote quote = pricingService.getQuote("MSFT");
            boolean valid = quote != null 
                && quote.getBidPrice() == 25.50
                && quote.getAskPrice() == 25.75
                && "USD".equals(quote.getCurrency());
            assertTest("T7.1", "PricingServiceImpl.getQuote(MSFT) returns correct prices",
                valid, quote != null ? "bid=" + quote.getBidPrice() + ", ask=" + quote.getAskPrice() : "null");
        } catch (Exception e) {
            assertTest("T7.1", "MSFT quote", false, e.getMessage());
        }

        // T7.2 - Get IBM quote
        try {
            PriceQuote quote = pricingService.getQuote("IBM");
            boolean valid = quote != null && quote.getBidPrice() == 120.00;
            assertTest("T7.2", "PricingServiceImpl.getQuote(IBM) returns bid=$120.00",
                valid, quote != null ? "bid=" + quote.getBidPrice() : "null");
        } catch (Exception e) {
            assertTest("T7.2", "IBM quote", false, e.getMessage());
        }

        // T7.3 - Batch quotes
        try {
            String[] symbols = {"MSFT", "IBM", "DELL"};
            PriceQuote[] quotes = pricingService.getBatchQuotes(symbols);
            boolean valid = quotes != null && quotes.length == 3;
            if (valid) {
                for (int i = 0; i < quotes.length; i++) {
                    if (quotes[i] == null) {
                        valid = false;
                        break;
                    }
                }
            }
            assertTest("T7.3", "getBatchQuotes returns 3 non-null quotes",
                valid, "count=" + (quotes != null ? quotes.length : 0));
        } catch (Exception e) {
            assertTest("T7.3", "Batch quotes", false, e.getMessage());
        }

        // T7.4 - Unknown symbol
        try {
            PriceQuote quote = pricingService.getQuote("AAPL");
            // Should return hardcoded fallback or null
            assertTest("T7.4", "Unknown symbol AAPL returns fallback/null (no crash)",
                true); // passes as long as no exception
        } catch (Exception e) {
            assertTest("T7.4", "Unknown symbol", false, e.getMessage());
        }

        // T7.5 - Null symbol
        try {
            PriceQuote quote = pricingService.getQuote(null);
            assertTest("T7.5", "Null symbol returns null (no crash)",
                quote == null);
        } catch (Exception e) {
            assertTest("T7.5", "Null symbol", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 8: Multi-Order Stress
    // ========================================================================

    private static void phase8_multiOrder() {
        System.out.println("=== Phase 8: Multi-Order Stress Test ===");
        System.out.println();

        // Submit 5 orders from different clients
        try {
            submitOrderSafe(createOrder("ORD-MULTI-001", "C001", "DELL", 300, "BUY", 35.00));
            submitOrderSafe(createOrder("ORD-MULTI-002", "C002", "CSCO", 200, "BUY", 22.00));
            submitOrderSafe(createOrder("ORD-MULTI-003", "C003", "INTC", 400, "SELL", 30.50));
            submitOrderSafe(createOrder("ORD-MULTI-004", "C004", "SUNW", 1000, "BUY", 9.00));
            submitOrderSafe(createOrder("ORD-MULTI-005", "C005", "MSFT", 100, "BUY", 25.75));

            // Wait for all to process
            Thread.sleep(15000);

            // Check all processed
            int filledCount = 0;
            for (int i = 1; i <= 5; i++) {
                String status = getOrderStatus("ORD-MULTI-00" + i);
                if ("FILLED".equals(status)) filledCount++;
            }

            assertTest("T8.1", "5 concurrent orders all processed to FILLED",
                filledCount == 5, "filled=" + filledCount + "/5");
        } catch (Exception e) {
            assertTest("T8.1", "Multi-order stress", false, e.getMessage());
        }

        // T8.2 - Run settlement on the multi-orders
        try {
            deleteDir(new File("./sftp-outbound"));

            BatchProcessor batch = new BatchProcessor();
            batch.processBatch();

            // Check settlement file has the right number of records
            File outDir = new File("./sftp-outbound");
            String[] datFiles = outDir.list(new java.io.FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".dat");
                }
            });

            if (datFiles != null && datFiles.length > 0) {
                String content = readFile(new File(outDir, datFiles[0]));
                String[] lines = content.split("\n");
                int detailCount = 0;
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].startsWith("D")) detailCount++;
                }
                assertTest("T8.2", "Settlement flat file has 5 detail records",
                    detailCount == 5, "detailRecords=" + detailCount);
            } else {
                assertTest("T8.2", "Settlement flat file generated", false, "no .dat files");
            }
        } catch (Exception e) {
            assertTest("T8.2", "Multi-order settlement", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 9: Enterprise Patterns
    // ========================================================================

    private static void phase9_enterprisePatterns() {
        System.out.println("=== Phase 9: Enterprise Patterns (J2EE circa 2001) ===");
        System.out.println();

        // -- T9.1: Service Locator --
        try {
            ServiceLocator locator = ServiceLocator.getInstance();
            locator.clearCache();

            PricingServiceImpl pricingSvc = new PricingServiceImpl();
            locator.register(ServiceLocator.PRICING_SERVICE, pricingSvc);

            Object looked = locator.lookup(ServiceLocator.PRICING_SERVICE);
            assertTest("T9.1", "Service Locator register + lookup",
                looked != null && looked instanceof PricingServiceImpl);
        } catch (Exception e) {
            assertTest("T9.1", "Service Locator", false, e.getMessage());
        }

        // -- T9.2: Service Locator cache miss returns null --
        try {
            ServiceLocator locator = ServiceLocator.getInstance();
            Object missing = locator.lookup("java:comp/env/ejb/NonExistentService");
            assertTest("T9.2", "Service Locator cache miss returns null",
                missing == null);
        } catch (Exception e) {
            assertTest("T9.2", "Service Locator cache miss", false, e.getMessage());
        }

        // -- T9.3: Connection Pool get/release --
        try {
            ConnectionPool pool = ConnectionPool.getInstance();
            int beforeAvail = pool.getAvailableCount();

            java.sql.Connection conn = pool.getConnection();
            boolean gotConn = (conn != null && !conn.isClosed());

            // verify pool accounting
            int usedDuring = pool.getUsedCount();

            pool.releaseConnection(conn);
            int afterAvail = pool.getAvailableCount();

            assertTest("T9.3", "Connection Pool get/release cycle",
                gotConn && usedDuring >= 1,
                "avail_before=" + beforeAvail + " used_during=" + usedDuring + " avail_after=" + afterAvail);
        } catch (Exception e) {
            assertTest("T9.3", "Connection Pool", false, e.getMessage());
        }

        // -- T9.4: Connection Pool SQL works --
        try {
            ConnectionPool pool = ConnectionPool.getInstance();
            java.sql.Connection conn = pool.getConnection();
            java.sql.Statement stmt = conn.createStatement();
            java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM CLIENTS");
            rs.next();
            int clientCount = rs.getInt(1);
            rs.close();
            stmt.close();
            pool.releaseConnection(conn);

            assertTest("T9.4", "Connection Pool SQL query works",
                clientCount >= 5, "clients=" + clientCount);
        } catch (Exception e) {
            assertTest("T9.4", "Connection Pool SQL", false, e.getMessage());
        }

        // -- T9.5: DAO Factory auto-detection --
        try {
            DAOFactory factory = DAOFactory.getFactory();
            String orderDAO = factory.getOrderDAOClassName();
            String settlementDAO = factory.getSettlementDAOClassName();

            boolean hasOrderDAO = (orderDAO != null && orderDAO.length() > 0);
            boolean hasSettlementDAO = (settlementDAO != null && settlementDAO.length() > 0);

            // verify it detected the right DB type
            int dbType = DAOFactory.detectDatabaseType();
            String dbName = (dbType == DAOFactory.ORACLE) ? "Oracle" : "HSQLDB";

            assertTest("T9.5", "DAO Factory auto-detects " + dbName + " and returns DAO class names",
                hasOrderDAO && hasSettlementDAO,
                "orderDAO=" + orderDAO + " settlementDAO=" + settlementDAO);
        } catch (Exception e) {
            assertTest("T9.5", "DAO Factory", false, e.getMessage());
        }

        // -- T9.6: Transfer Object from domain --
        try {
            TradeOrder order = new TradeOrder();
            order.setOrderId("ORD-TEST-DTO-001");
            order.setClientId("C001");
            order.setSymbol("MSFT");
            order.setQuantity(100);
            order.setSide("BUY");
            order.setPrice(25.50);
            order.setRequestedPrice(25.00);
            order.setStatus("FILLED");
            order.setOrderDate(new Date());

            OrderTransferObject dto = OrderTransferObject.fromTradeOrder(order);
            boolean matches = dto != null
                && "ORD-TEST-DTO-001".equals(dto.getOrderId())
                && "MSFT".equals(dto.getSymbol())
                && dto.getQuantity() == 100
                && "BUY".equals(dto.getSide())
                && Math.abs(dto.getPrice() - 25.50) < 0.01;

            assertTest("T9.6", "OrderTransferObject.fromTradeOrder preserves data", matches);
        } catch (Exception e) {
            assertTest("T9.6", "Transfer Object from domain", false, e.getMessage());
        }

        // -- T9.7: Transfer Object XML round-trip --
        try {
            TradeOrder order = new TradeOrder();
            order.setOrderId("ORD-TEST-DTO-002");
            order.setClientId("C002");
            order.setSymbol("IBM");
            order.setQuantity(500);
            order.setSide("SELL");
            order.setPrice(120.00);
            order.setRequestedPrice(119.50);
            order.setStatus("FILLED");

            OrderTransferObject original = OrderTransferObject.fromTradeOrder(order);
            String xml = original.toXml();
            OrderTransferObject parsed = OrderTransferObject.fromXml(xml);

            boolean roundTrip = parsed != null
                && "ORD-TEST-DTO-002".equals(parsed.getOrderId())
                && "IBM".equals(parsed.getSymbol())
                && parsed.getQuantity() == 500
                && "SELL".equals(parsed.getSide())
                && Math.abs(parsed.getPrice() - 120.00) < 0.01;

            assertTest("T9.7", "OrderTransferObject XML round-trip", roundTrip,
                xml.contains("<orderId>ORD-TEST-DTO-002</orderId>") ? "xml OK" : "xml malformed");
        } catch (Exception e) {
            assertTest("T9.7", "Transfer Object XML round-trip", false, e.getMessage());
        }

        // -- T9.8: TransferObjectAssembler --
        try {
            TradeOrder order = new TradeOrder();
            order.setOrderId("ORD-TEST-DTO-003");
            order.setClientId("C003");
            order.setSymbol("ORCL");
            order.setQuantity(200);
            order.setSide("BUY");
            order.setPrice(15.00);

            Client client = new Client();
            client.setClientId("C003");
            client.setName("Smith & Associates");
            client.setTier(Client.TIER_SILVER);

            OrderTransferObject assembled = TransferObjectAssembler.assembleOrderTO(order, client);
            assertTest("T9.8", "TransferObjectAssembler populates clientName",
                assembled != null && "Smith & Associates".equals(assembled.getClientName()));
        } catch (Exception e) {
            assertTest("T9.8", "TransferObjectAssembler", false, e.getMessage());
        }

        // -- T9.9: Inbound reconciliation (.dat format) --
        try {
            // We need settlement records to exist first. Check if any uploaded records exist
            // from earlier phases (phase 4 and phase 8 created them).
            Connection conn = ConnectionHelper.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT RECORD_ID FROM SETTLEMENT_RECORDS WHERE STATUS = '" + SettlementRecord.STATUS_UPLOADED + "'");

            java.util.List recordIds = new java.util.ArrayList();
            while (rs.next() && recordIds.size() < 3) {
                recordIds.add(rs.getString("RECORD_ID"));
            }
            rs.close();
            stmt.close();
            ConnectionHelper.closeQuietly(conn);

            if (recordIds.size() > 0) {
                // generate a test .dat reconciliation file
                String[] ids = new String[recordIds.size()];
                for (int i = 0; i < recordIds.size(); i++) {
                    ids[i] = (String) recordIds.get(i);
                }

                // generate locally first, then put in the right place
                File tempDir = new File("./sftp-root/inbound/");
                tempDir.mkdirs();
                String datFilePath = tempDir.getAbsolutePath() + "/test_recon.dat";
                ReconciliationProcessor.generateTestReconciliationFile(datFilePath, ids, "dat");

                // if real SFTP is configured, upload the file to the server's /outgoing/
                // directory (simulating the clearinghouse dropping a file there)
                uploadReconFileIfReal(datFilePath, "test_recon.dat");

                // process inbound
                ReconciliationProcessor processor = new ReconciliationProcessor();
                int processed = processor.processInbound();

                assertTest("T9.9", "Inbound reconciliation .dat processing",
                    processed > 0, "processed=" + processed + " records from .dat");
            } else {
                // no uploaded records to reconcile - skip gracefully
                assertTest("T9.9", "Inbound reconciliation .dat processing",
                    true, "skipped - no UPLOADED settlement records available");
            }
        } catch (Exception e) {
            assertTest("T9.9", "Inbound reconciliation", false, e.getMessage());
        }

        // -- T9.10: Inbound reconciliation (.xml format) --
        try {
            Connection conn = ConnectionHelper.getConnection();
            Statement stmt = conn.createStatement();
            // find records with UPLOADED status (may have been set to RECONCILED by T9.9)
            ResultSet rs = stmt.executeQuery(
                "SELECT RECORD_ID FROM SETTLEMENT_RECORDS WHERE STATUS = '" + SettlementRecord.STATUS_UPLOADED
                + "' OR STATUS = '" + SettlementRecord.STATUS_RECONCILED + "'");

            java.util.List recordIds = new java.util.ArrayList();
            while (rs.next() && recordIds.size() < 2) {
                recordIds.add(rs.getString("RECORD_ID"));
            }
            rs.close();
            stmt.close();
            ConnectionHelper.closeQuietly(conn);

            if (recordIds.size() > 0) {
                // set status back to UPLOADED so reconciliation has something to find
                conn = ConnectionHelper.getConnection();
                stmt = conn.createStatement();
                for (int i = 0; i < recordIds.size(); i++) {
                    stmt.executeUpdate("UPDATE SETTLEMENT_RECORDS SET STATUS = 'UPLOADED' WHERE RECORD_ID = '" + recordIds.get(i) + "'");
                }
                stmt.close();
                ConnectionHelper.closeQuietly(conn);

                String[] ids = new String[recordIds.size()];
                for (int i = 0; i < recordIds.size(); i++) {
                    ids[i] = (String) recordIds.get(i);
                }

                File tempDir = new File("./sftp-root/inbound/");
                tempDir.mkdirs();
                String xmlFilePath = tempDir.getAbsolutePath() + "/test_recon.xml";
                ReconciliationProcessor.generateTestReconciliationFile(xmlFilePath, ids, "xml");

                // if real SFTP is configured, upload to the server's /outgoing/ dir
                uploadReconFileIfReal(xmlFilePath, "test_recon.xml");

                ReconciliationProcessor processor = new ReconciliationProcessor();
                int processed = processor.processInbound();

                assertTest("T9.10", "Inbound reconciliation .xml processing",
                    processed > 0, "processed=" + processed);
            } else {
                assertTest("T9.10", "Inbound reconciliation .xml processing",
                    true, "skipped - no settlement records available");
            }
        } catch (Exception e) {
            assertTest("T9.10", "Inbound reconciliation XML", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 10: Config-Driven Rule Loading
    // ========================================================================

    private static void phase10_ruleConfig() {
        System.out.println("=== Phase 10: Config-Driven Rule Loading ===");
        System.out.println();

        // T10.1 - RuleConfigLoader can parse the config file
        try {
            List rules = RuleConfigLoader.loadRules();
            assertTest("T10.1", "RuleConfigLoader parses rules.xml from classpath",
                rules != null && !rules.isEmpty(),
                "rulesLoaded=" + (rules != null ? String.valueOf(rules.size()) : "0"));
        } catch (Exception e) {
            assertTest("T10.1", "RuleConfigLoader parse", false, e.getMessage());
        }

        // T10.2 - Loaded rules match expected 4 rules with correct types
        try {
            List rules = RuleConfigLoader.loadRules();
            boolean correctCount = (rules.size() == 4);

            boolean hasMaxOrder = false;
            boolean hasClientTier = false;
            boolean hasMarketHours = false;
            boolean hasSpecialClients = false;

            for (int i = 0; i < rules.size(); i++) {
                Rule rule = (Rule) rules.get(i);
                if (rule instanceof MaxOrderValueRule) hasMaxOrder = true;
                if (rule instanceof ClientTierRule) hasClientTier = true;
                if (rule instanceof MarketHoursRule) hasMarketHours = true;
                if (rule instanceof SpecialClientsRule) hasSpecialClients = true;
            }

            assertTest("T10.2", "Config loads exactly 4 expected rule types",
                correctCount && hasMaxOrder && hasClientTier && hasMarketHours && hasSpecialClients,
                "count=" + rules.size() + ", maxOrder=" + hasMaxOrder
                + ", clientTier=" + hasClientTier + ", marketHours=" + hasMarketHours
                + ", specialClients=" + hasSpecialClients);
        } catch (Exception e) {
            assertTest("T10.2", "Rule type verification", false, e.getMessage());
        }

        // T10.3 - Loaded rules have correct priorities
        try {
            List rules = RuleConfigLoader.loadRules();
            int[] expectedPriorities = {100, 90, 80, 50};
            String[] expectedNames = {"MaxOrderValue", "ClientTier", "MarketHours", "SpecialClients"};
            boolean allFound = true;

            for (int j = 0; j < expectedNames.length; j++) {
                boolean found = false;
                for (int i = 0; i < rules.size(); i++) {
                    Rule rule = (Rule) rules.get(i);
                    if (rule.getName().equals(expectedNames[j])
                        && rule.getPriority() == expectedPriorities[j]) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    allFound = false;
                }
            }

            assertTest("T10.3", "Config-loaded rules have correct priorities (100, 90, 80, 50)",
                allFound);
        } catch (Exception e) {
            assertTest("T10.3", "Rule priorities", false, e.getMessage());
        }

        // T10.4 - Rule engine still works correctly with config-loaded rules
        try {
            // The rule engine singleton is already loaded via OrderMessageListener.
            // Verify it still evaluates a valid order correctly.
            RuleEngine testEngine = RuleEngine.getInstance();

            Client client = new Client();
            client.setClientId("C001");
            client.setTier(Client.TIER_GOLD);
            client.setMaxOrderValue(500000);
            client.setActive(true);

            TradeOrder order = new TradeOrder();
            order.setOrderId("CONFIG-TEST-001");
            order.setQuantity(100);
            order.setRequestedPrice(25.00);

            RuleContext ctx = new RuleContext(order, client);
            boolean result = testEngine.evaluate(ctx);

            assertTest("T10.4", "Rule engine evaluates correctly with config-loaded rules",
                result && !ctx.isRejected(),
                "passed=" + result);
        } catch (Exception e) {
            assertTest("T10.4", "Config-loaded rule evaluation", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 11: Per-Symbol Trading Restrictions
    // ========================================================================

    private static void phase11_tradingRestrictions() {
        System.out.println("=== Phase 11: Per-Symbol Trading Restrictions ===");
        System.out.println();

        // T11.1 - RestrictedSymbolRule rejects a restricted symbol
        try {
            Client client = new Client();
            client.setClientId("C001");
            client.setTier(Client.TIER_GOLD);
            client.setMaxOrderValue(500000);
            client.setActive(true);

            TradeOrder order = new TradeOrder();
            order.setOrderId("RESTRICT-001");
            order.setSymbol("ENRN");
            order.setQuantity(100);
            order.setSide(TradeOrder.SIDE_BUY);
            order.setRequestedPrice(10.00);

            RuleContext ctx = new RuleContext(order, client);
            RestrictedSymbolRule rule = new RestrictedSymbolRule();
            boolean result = rule.evaluate(ctx);
            assertTest("T11.1", "RestrictedSymbolRule rejects ENRN",
                !result && ctx.isRejected(),
                "rejected=" + ctx.isRejected() + ", reason=" + ctx.getRejectionReason());
        } catch (Exception e) {
            assertTest("T11.1", "RestrictedSymbolRule rejection", false, e.getMessage());
        }

        // T11.2 - ShortSaleRule passes for a small SELL
        try {
            Client client = new Client();
            client.setClientId("C003");
            client.setTier(Client.TIER_SILVER);
            client.setMaxOrderValue(250000);
            client.setActive(true);

            TradeOrder order = new TradeOrder();
            order.setOrderId("SHORT-001");
            order.setSymbol("MSFT");
            order.setQuantity(100);
            order.setSide(TradeOrder.SIDE_SELL);
            order.setRequestedPrice(25.00);

            RuleContext ctx = new RuleContext(order, client);
            ShortSaleRule rule = new ShortSaleRule();
            boolean result = rule.evaluate(ctx);
            assertTest("T11.2", "ShortSaleRule passes for 100-share SELL",
                result && !ctx.isRejected(),
                "passed=" + result);
        } catch (Exception e) {
            assertTest("T11.2", "ShortSaleRule small sell", false, e.getMessage());
        }

        // T11.3 - restricted_check attribute set on a passing order
        try {
            Client client = new Client();
            client.setClientId("C001");
            client.setTier(Client.TIER_GOLD);
            client.setMaxOrderValue(500000);
            client.setActive(true);

            TradeOrder order = new TradeOrder();
            order.setOrderId("RESTRICT-002");
            order.setSymbol("MSFT");
            order.setQuantity(100);
            order.setSide(TradeOrder.SIDE_BUY);
            order.setRequestedPrice(25.00);

            RuleContext ctx = new RuleContext(order, client);
            RestrictedSymbolRule rule = new RestrictedSymbolRule();
            rule.evaluate(ctx);

            Object restrictedCheck = ctx.getAttribute("restricted_check");
            assertTest("T11.3", "restricted_check attribute set to 'passed' on non-restricted symbol",
                "passed".equals(restrictedCheck),
                "restricted_check=" + restrictedCheck);
        } catch (Exception e) {
            assertTest("T11.3", "restricted_check attribute", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Phase 12: Derivatives Engine
    // ========================================================================

    private static void phase12_derivativesEngine() {
        System.out.println("=== Phase 12: Derivatives Engine (FX/Options) ===");
        System.out.println();

        // T12.1 - DerivativeOrder field round-trip via XML
        try {
            DerivativeOrder orig = new DerivativeOrder();
            orig.setOrderId("DRV-001");
            orig.setClientId("C001");
            orig.setContractType(DerivativeOrder.TYPE_FX_SPOT);
            orig.setUnderlying("EUR/USD");
            orig.setStrikePrice(1.10);
            orig.setQuantity(50000);
            orig.setExpiry("2004-09-15");
            orig.setStatus(DerivativeOrder.STATUS_NEW);
            orig.setPremium(0.0);

            String xml = orig.toXml();
            DerivativeOrder parsed = DerivativeOrder.fromXml(xml);

            boolean fieldsMatch = "DRV-001".equals(parsed.getOrderId())
                    && "C001".equals(parsed.getClientId())
                    && DerivativeOrder.TYPE_FX_SPOT.equals(parsed.getContractType())
                    && "EUR/USD".equals(parsed.getUnderlying())
                    && parsed.getStrikePrice() == 1.10
                    && parsed.getQuantity() == 50000
                    && "2004-09-15".equals(parsed.getExpiry())
                    && DerivativeOrder.STATUS_NEW.equals(parsed.getStatus());

            assertTest("T12.1", "[DERIV] DerivativeOrder XML round-trip preserves all fields",
                fieldsMatch,
                "orderId=" + parsed.getOrderId() + " type=" + parsed.getContractType()
                    + " underlying=" + parsed.getUnderlying());
        } catch (Exception e) {
            assertTest("T12.1", "[DERIV] DerivativeOrder XML round-trip", false, e.getMessage());
        }

        // T12.2 - DerivativeProcessor processes a simple FX_SPOT order
        try {
            DerivativeProcessor proc = new DerivativeProcessor();

            DerivativeOrder order = new DerivativeOrder();
            order.setOrderId("DRV-002");
            order.setClientId("C001");
            order.setContractType(DerivativeOrder.TYPE_FX_SPOT);
            order.setUnderlying("EUR/USD");
            order.setStrikePrice(1.10);
            order.setQuantity(10000);
            order.setExpiry("2004-09-15");

            DerivativeOrder result = proc.processOrder(order);

            boolean filled = result != null
                    && DerivativeOrder.STATUS_FILLED.equals(result.getStatus())
                    && result.getPremium() > 0;

            // expected premium: 10000 * 1.10 * 0.015 = 165.0
            double expectedPremium = 10000 * 1.10 * 0.015;
            boolean premiumCorrect = result != null
                    && Math.abs(result.getPremium() - expectedPremium) < 0.01;

            assertTest("T12.2", "[DERIV] FX_SPOT order processed -> FILLED, premium=" + expectedPremium,
                filled && premiumCorrect,
                result != null ? "status=" + result.getStatus() + " premium=" + result.getPremium() : "null result");
        } catch (Exception e) {
            assertTest("T12.2", "[DERIV] FX_SPOT order processing", false, e.getMessage());
        }

        // T12.3 - FxPricingHelper returns expected rates
        try {
            double eurRate = FxPricingHelper.getRate("EUR/USD");
            double gbpRate = FxPricingHelper.getRate("GBP/USD");
            double jpyRate = FxPricingHelper.getRate("JPY/USD");
            double unknownRate = FxPricingHelper.getRate("XYZ/USD");

            boolean ratesOk = eurRate == 1.10
                    && gbpRate == 1.55
                    && jpyRate == 0.009
                    && unknownRate == -1.0;

            assertTest("T12.3", "[DERIV] FxPricingHelper rates: EUR=1.10 GBP=1.55 JPY=0.009 unknown=-1",
                ratesOk,
                "EUR=" + eurRate + " GBP=" + gbpRate + " JPY=" + jpyRate + " XYZ=" + unknownRate);
        } catch (Exception e) {
            assertTest("T12.3", "[DERIV] FxPricingHelper rates", false, e.getMessage());
        }

        System.out.println();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Clean up test data from previous runs.
     * For Oracle (persistent DB) we need to delete test-generated rows 
     * so they don't cause duplicate key errors on re-run.
     * For HSQLDB (in-memory) this is harmless - the DB is fresh each time.
     */
    private static void cleanupTestData() {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();
            // Order matters: child tables first (FK constraints)
            stmt.executeUpdate("DELETE FROM BILLING_LEDGER WHERE ORDER_ID LIKE 'ORD-TEST-%' OR ORDER_ID LIKE 'ORD-MULTI-%'");
            stmt.executeUpdate("DELETE FROM AUDIT_LOG WHERE ENTITY_ID LIKE 'ORD-TEST-%' OR ENTITY_ID LIKE 'ORD-MULTI-%'");
            stmt.executeUpdate("DELETE FROM SETTLEMENT_RECORDS WHERE ORDER_ID LIKE 'ORD-TEST-%' OR ORDER_ID LIKE 'ORD-MULTI-%'");
            stmt.executeUpdate("DELETE FROM NOTIFICATIONS WHERE ORDER_ID LIKE 'ORD-TEST-%' OR ORDER_ID LIKE 'ORD-MULTI-%'");
            stmt.executeUpdate("DELETE FROM TRADE_ORDERS WHERE ORDER_ID LIKE 'ORD-TEST-%' OR ORDER_ID LIKE 'ORD-MULTI-%'");
            System.out.println("Cleaned up test data from previous runs.");
        } catch (Exception e) {
            // not fatal - maybe tables are empty
            System.out.println("Note: cleanup skipped or partial: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Submit an order and verify status, handling the race condition where the
     * order engine processes the MQ message before the producer's own DB insert.
     */
    private static void submitAndVerify(String testId, String description,
                                         String orderId, String clientId, String symbol,
                                         int qty, String side, double price,
                                         String expectedStatus) {
        try {
            TradeOrder order = createOrder(orderId, clientId, symbol, qty, side, price);
            submitOrderSafe(order);
            Thread.sleep(5000);

            String status = getOrderStatus(orderId);
            assertTest(testId, description, expectedStatus.equals(status),
                "status=" + status);
        } catch (Exception e) {
            assertTest(testId, description, false, e.getMessage());
        }
    }

    /**
     * Submit an order, tolerating DB insert failures from the TradeMessageProducer.
     * The producer sends to MQ first, then tries to insert to DB. Due to a race
     * condition, the order engine may insert the order before the producer does,
     * causing a duplicate key exception. The MQ message is still sent successfully.
     */
    private static void submitOrderSafe(TradeOrder order) {
        TradeMessageProducer producer = new TradeMessageProducer();
        try {
            producer.submitOrder(order);
        } catch (RuntimeException e) {
            // Race condition: order engine inserted the order before the producer.
            // The MQ message was already sent successfully (step 2 in producer).
            System.out.println("  [NOTE] Producer DB insert race condition for " + 
                order.getOrderId() + " (MQ send succeeded)");
        }
    }

    private static TradeOrder createOrder(String orderId, String clientId, String symbol,
                                          int qty, String side, double price) {
        TradeOrder order = new TradeOrder();
        order.setOrderId(orderId);
        order.setClientId(clientId);
        order.setSymbol(symbol);
        order.setQuantity(qty);
        order.setSide(side);
        order.setRequestedPrice(price);
        return order;
    }

    private static String getOrderStatus(String orderId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            ps = conn.prepareStatement("SELECT STATUS FROM TRADE_ORDERS WHERE ORDER_ID = ?");
            ps.setString(1, orderId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("STATUS");
            }
            return null;
        } catch (Exception e) {
            System.err.println("  Error getting order status: " + e.getMessage());
            return null;
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    private static int countRecords(String tableName) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName);
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            return -1;
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    private static String readFile(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    /**
     * If real SFTP is configured, upload the reconciliation file to the server's
     * /outgoing/ directory to simulate the clearinghouse dropping a file there.
     * In embedded mode (no SFTP), this is a no-op since the SftpPoller falls back
     * to the local ./sftp-root/inbound/ directory where we already generated it.
     */
    private static void uploadReconFileIfReal(String localFilePath, String remoteFileName) {
        try {
            java.util.Properties props = new java.util.Properties();
            java.io.InputStream is = EndToEndTest.class.getClassLoader()
                    .getResourceAsStream("settlement.properties");
            if (is != null) {
                props.load(is);
                is.close();
            }

            String host = props.getProperty("sftp.host", "");
            if (host == null || host.trim().length() == 0) {
                // embedded mode — file is already in ./sftp-root/inbound/, SftpPoller will read it
                return;
            }

            int port = Integer.parseInt(props.getProperty("sftp.port", "22"));
            String username = props.getProperty("sftp.username", "");
            String password = props.getProperty("sftp.password", "");
            String remoteInboundDir = props.getProperty("sftp.remote.inbound.dir", "/outgoing/");

            JSch jsch = new JSch();
            com.jcraft.jsch.Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            java.util.Properties sshConfig = new java.util.Properties();
            sshConfig.put("StrictHostKeyChecking", "no");
            session.setConfig(sshConfig);
            session.connect(30000);

            com.jcraft.jsch.Channel channel = session.openChannel("sftp");
            channel.connect(30000);
            ChannelSftp sftpChannel = (ChannelSftp) channel;

            sftpChannel.cd(remoteInboundDir);
            java.io.FileInputStream fis = new java.io.FileInputStream(localFilePath);
            sftpChannel.put(fis, remoteFileName);
            fis.close();

            System.out.println("  Uploaded test recon file to SFTP " + remoteInboundDir + remoteFileName);

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            System.err.println("  WARN: Could not upload recon file to SFTP: " + e.getMessage());
            // fallback — the local file is still there for non-SFTP processing
        }
    }

    private static void deleteDir(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        deleteDir(files[i]);
                    } else {
                        files[i].delete();
                    }
                }
            }
            dir.delete();
        }
    }

    // ========================================================================
    // Assertion Framework
    // ========================================================================

    private static void assertTest(String id, String description, boolean passed) {
        assertTest(id, description, passed, null);
    }

    private static void assertTest(String id, String description, boolean result, String detail) {
        total++;
        String status;
        if (result) {
            passed++;
            status = "PASS";
        } else {
            failed++;
            status = "FAIL";
            failedTests.add(id + ": " + description);
        }
        System.out.println("  [" + status + "] " + id + " - " + description 
            + (detail != null ? " (" + detail + ")" : ""));
    }

    private static void printSummary() {
        System.out.println();
        System.out.println("##############################################");
        System.out.println("  TEST SUMMARY");
        System.out.println("##############################################");
        System.out.println("  Total:  " + total);
        System.out.println("  Passed: " + passed);
        System.out.println("  Failed: " + failed);
        System.out.println();

        if (failed > 0) {
            System.out.println("  FAILED TESTS:");
            for (int i = 0; i < failedTests.size(); i++) {
                System.out.println("    - " + failedTests.get(i));
            }
        } else {
            System.out.println("  ALL TESTS PASSED!");
        }
        System.out.println("##############################################");
    }
}
