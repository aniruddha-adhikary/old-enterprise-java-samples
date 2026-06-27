package com.bigcorp.settlement.regulatory;

import com.bigcorp.common.db.ConnectionHelper;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Regulatory reporting export job (MiFID II / CAT-style).
 * 
 * Generates fixed-width and XML regulatory report files for
 * submission to the regulator. Mirrors the settlement-gateway's
 * file generation patterns.
 * 
 * Added after the 2021 regulatory review (REG-2021-001).
 * The regulator requires daily reporting of all executed trades
 * in a specific fixed-width format (REG-FW-001) and XML format
 * (REG-XML-001).
 * 
 * Defensive null checks everywhere because "if we submit a null
 * field, the regulator's parser crashes and they fine us."
 * 
 * @author compliance-bolt-on
 * @since 2021-Q1
 */
public class RegulatoryExportJob {

    // Fixed-width field widths (REG-FW-001 specification)
    private static final int FW_ORDER_ID = 20;
    private static final int FW_CLIENT_ID = 10;
    private static final int FW_SYMBOL = 10;
    private static final int FW_SIDE = 4;
    private static final int FW_QTY = 12;
    private static final int FW_PRICE = 15;
    private static final int FW_STATUS = 12;
    private static final int FW_DATE = 19;

    private static final SimpleDateFormat REG_DATE_FMT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final SimpleDateFormat FILE_DATE_FMT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    /**
     * Run the regulatory export job.
     * Generates both fixed-width and XML files.
     * Returns the number of files generated.
     */
    public int runExport(String outputDir) {
        // Defensive null check
        if (outputDir == null || outputDir.trim().length() == 0) {
            outputDir = "./regulatory-output/";
        }

        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        int filesGenerated = 0;
        String timestamp = FILE_DATE_FMT.format(new Date());

        try {
            String fwFile = outputDir + "/REG_REPORT_" + timestamp + ".dat";
            int fwRecords = generateFixedWidthReport(fwFile);
            if (fwRecords >= 0) {
                filesGenerated++;
                logReport("FIXED_WIDTH", fwFile, fwRecords, "SUCCESS");
            }
        } catch (Exception e) {
            System.err.println("WARN: Fixed-width regulatory report failed: " + e.getMessage());
            logReport("FIXED_WIDTH", "N/A", 0, "FAILED: " + e.getMessage());
        }

        try {
            String xmlFile = outputDir + "/REG_REPORT_" + timestamp + ".xml";
            int xmlRecords = generateXmlReport(xmlFile);
            if (xmlRecords >= 0) {
                filesGenerated++;
                logReport("XML", xmlFile, xmlRecords, "SUCCESS");
            }
        } catch (Exception e) {
            System.err.println("WARN: XML regulatory report failed: " + e.getMessage());
            logReport("XML", "N/A", 0, "FAILED: " + e.getMessage());
        }

        return filesGenerated;
    }

    /**
     * Generate fixed-width regulatory report.
     * Returns number of records written.
     */
    public int generateFixedWidthReport(String outputPath) throws Exception {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        FileWriter fw = null;
        int recordCount = 0;

        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                System.err.println("ERROR: No DB connection for regulatory export");
                return -1;
            }

            stmt = conn.createStatement();
            rs = stmt.executeQuery(
                "SELECT ORDER_ID, CLIENT_ID, SYMBOL, SIDE, QUANTITY, PRICE, STATUS, ORDER_DATE " +
                "FROM TRADE_ORDERS WHERE STATUS IN ('FILLED', 'SETTLED') " +
                "ORDER BY ORDER_DATE ASC"
            );

            fw = new FileWriter(outputPath);

            // Header record
            fw.write("HDR" + padRight("BIGCORP_REG_REPORT", 30) + padRight(REG_DATE_FMT.format(new Date()), 19) + "\n");

            while (rs.next()) {
                // Defensive null checks on every field
                String orderId = rs.getString("ORDER_ID");
                if (orderId == null) orderId = "";
                String clientId = rs.getString("CLIENT_ID");
                if (clientId == null) clientId = "";
                String symbol = rs.getString("SYMBOL");
                if (symbol == null) symbol = "";
                String side = rs.getString("SIDE");
                if (side == null) side = "";
                int qty = rs.getInt("QUANTITY");
                double price = rs.getDouble("PRICE");
                String status = rs.getString("STATUS");
                if (status == null) status = "";
                String orderDate = rs.getString("ORDER_DATE");
                if (orderDate == null) orderDate = "";

                StringBuffer line = new StringBuffer();
                line.append("DTL");
                line.append(padRight(orderId, FW_ORDER_ID));
                line.append(padRight(clientId, FW_CLIENT_ID));
                line.append(padRight(symbol, FW_SYMBOL));
                line.append(padRight(side, FW_SIDE));
                line.append(padLeft(String.valueOf(qty), FW_QTY));
                line.append(padLeft(formatPrice(price), FW_PRICE));
                line.append(padRight(status, FW_STATUS));
                line.append(padRight(orderDate.length() > FW_DATE ? orderDate.substring(0, FW_DATE) : orderDate, FW_DATE));
                line.append("\n");

                fw.write(line.toString());
                recordCount++;
            }

            // Trailer record
            fw.write("TRL" + padLeft(String.valueOf(recordCount), 10) + padRight(REG_DATE_FMT.format(new Date()), 19) + "\n");

        } finally {
            if (fw != null) try { fw.close(); } catch (Exception e) { }
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }

        return recordCount;
    }

    /**
     * Generate XML regulatory report.
     * Returns number of records written.
     */
    public int generateXmlReport(String outputPath) throws Exception {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        FileWriter fw = null;
        int recordCount = 0;

        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                return -1;
            }

            stmt = conn.createStatement();
            rs = stmt.executeQuery(
                "SELECT ORDER_ID, CLIENT_ID, SYMBOL, SIDE, QUANTITY, PRICE, STATUS, ORDER_DATE " +
                "FROM TRADE_ORDERS WHERE STATUS IN ('FILLED', 'SETTLED') " +
                "ORDER BY ORDER_DATE ASC"
            );

            fw = new FileWriter(outputPath);
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<RegulatoryReport>\n");
            fw.write("  <Header>\n");
            fw.write("    <Firm>BIGCORP</Firm>\n");
            fw.write("    <ReportDate>" + REG_DATE_FMT.format(new Date()) + "</ReportDate>\n");
            fw.write("    <ReportType>DAILY_TRADE_REPORT</ReportType>\n");
            fw.write("  </Header>\n");
            fw.write("  <Trades>\n");

            while (rs.next()) {
                // Defensive null checks — duplicate of the fixed-width method
                String orderId = rs.getString("ORDER_ID");
                if (orderId == null) orderId = "";
                String clientId = rs.getString("CLIENT_ID");
                if (clientId == null) clientId = "";
                String symbol = rs.getString("SYMBOL");
                if (symbol == null) symbol = "";
                String side = rs.getString("SIDE");
                if (side == null) side = "";
                int qty = rs.getInt("QUANTITY");
                double price = rs.getDouble("PRICE");
                String status = rs.getString("STATUS");
                if (status == null) status = "";
                String orderDate = rs.getString("ORDER_DATE");
                if (orderDate == null) orderDate = "";

                fw.write("    <Trade>\n");
                fw.write("      <OrderId>" + escapeXml(orderId) + "</OrderId>\n");
                fw.write("      <ClientId>" + escapeXml(clientId) + "</ClientId>\n");
                fw.write("      <Symbol>" + escapeXml(symbol) + "</Symbol>\n");
                fw.write("      <Side>" + escapeXml(side) + "</Side>\n");
                fw.write("      <Quantity>" + qty + "</Quantity>\n");
                fw.write("      <Price>" + formatPrice(price) + "</Price>\n");
                fw.write("      <Status>" + escapeXml(status) + "</Status>\n");
                fw.write("      <TradeDate>" + escapeXml(orderDate) + "</TradeDate>\n");
                fw.write("    </Trade>\n");
                recordCount++;
            }

            fw.write("  </Trades>\n");
            fw.write("  <Trailer>\n");
            fw.write("    <RecordCount>" + recordCount + "</RecordCount>\n");
            fw.write("  </Trailer>\n");
            fw.write("</RegulatoryReport>\n");

        } finally {
            if (fw != null) try { fw.close(); } catch (Exception e) { }
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }

        return recordCount;
    }

    /**
     * Validate a regulatory report file.
     * Returns true if basic validation passes.
     * Added because "we submitted a corrupt file once and got fined."
     */
    public static boolean validateFixedWidthFile(String content) {
        // Redundant validation layer — compliance insists
        if (content == null) return false;
        if (content.trim().length() == 0) return false;

        // Must start with HDR
        if (!content.startsWith("HDR")) return false;

        // Must end with TRL line
        String[] lines = content.split("\n");
        if (lines.length < 2) return false;

        String lastLine = lines[lines.length - 1].trim();
        if (!lastLine.startsWith("TRL")) return false;

        return true;
    }

    /**
     * Log a regulatory report generation to REG_REPORT_LOG table.
     */
    private void logReport(String reportType, String filePath, int recordCount, String status) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) return;

            ps = conn.prepareStatement(
                "INSERT INTO REG_REPORT_LOG (REPORT_TYPE, FILE_PATH, RECORD_COUNT, STATUS, GENERATION_TIME) " +
                "VALUES (?, ?, ?, ?, ?)"
            );
            ps.setString(1, reportType != null ? reportType : "UNKNOWN");
            ps.setString(2, filePath != null ? filePath : "N/A");
            ps.setInt(3, recordCount);
            ps.setString(4, status != null ? status : "UNKNOWN");
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception e) {
            // Logging failure must NOT prevent report generation
            System.err.println("WARN: Failed to log regulatory report: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    // Utility methods

    private static String padRight(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        StringBuffer sb = new StringBuffer(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    private static String padLeft(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        StringBuffer sb = new StringBuffer();
        while (sb.length() + s.length() < len) sb.append(' ');
        sb.append(s);
        return sb.toString();
    }

    private static String formatPrice(double price) {
        // Format to 4 decimal places
        long scaled = Math.round(price * 10000);
        String s = String.valueOf(scaled);
        while (s.length() < 5) s = "0" + s;
        return s.substring(0, s.length() - 4) + "." + s.substring(s.length() - 4);
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
