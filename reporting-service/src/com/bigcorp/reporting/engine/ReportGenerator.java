package com.bigcorp.reporting.engine;

import com.bigcorp.reporting.config.ReportConfig;
import com.bigcorp.reporting.dao.ReportingDAO;
import com.bigcorp.reporting.template.ReportTemplateEngine;
import com.bigcorp.reporting.util.ReportLogger;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * Main report generation engine.
 * 
 * Orchestrates DAO queries and template rendering to produce
 * daily/monthly P&L, volume, billing, and settlement reports.
 * 
 * Each report is output in both HTML and CSV formats to the
 * configured output directory.
 * 
 * @author contractor (reporting team)
 * @since 2013-Q2
 */
public class ReportGenerator {

    private static final ReportLogger log = new ReportLogger(ReportGenerator.class);

    private ReportingDAO dao;

    public ReportGenerator() {
        this.dao = new ReportingDAO();
    }

    /**
     * Generate all reports to the output directory.
     * Returns the number of report files written.
     */
    public int generateAllReports() {
        int filesWritten = 0;

        String outputDir = ReportConfig.getOutputDir();
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        log.info("Generating all reports to: " + outputDir);

        try {
            filesWritten += generateDailyPnlReport(outputDir);
        } catch (Exception e) {
            log.error("Failed to generate daily P&L report", e);
        }

        try {
            filesWritten += generateMonthlyVolumeReport(outputDir);
        } catch (Exception e) {
            log.error("Failed to generate monthly volume report", e);
        }

        try {
            filesWritten += generateBillingReport(outputDir);
        } catch (Exception e) {
            log.error("Failed to generate billing report", e);
        }

        try {
            filesWritten += generateSettlementReport(outputDir);
        } catch (Exception e) {
            log.error("Failed to generate settlement report", e);
        }

        log.info("Report generation complete. Files written: " + filesWritten);
        return filesWritten;
    }

    /**
     * Generate the daily P&L report.
     * Returns number of files written (0, 1, or 2).
     */
    public int generateDailyPnlReport(String outputDir) throws Exception {
        log.info("Generating daily P&L report...");
        List data = dao.getDailyPnlSummary();

        String[] columns = {"TRADE_DATE", "SYMBOL", "SIDE", "TOTAL_QTY", "TOTAL_VALUE", "ORDER_COUNT"};
        String[] headers = {"Trade Date", "Symbol", "Side", "Total Qty", "Total Value ($)", "Order Count"};

        int written = 0;
        String html = ReportTemplateEngine.generateHtmlReport("Daily P&L Summary", columns, headers, data);
        writeFile(outputDir + "/daily_pnl.html", html);
        written++;

        String csv = ReportTemplateEngine.generateCsvReport(columns, headers, data);
        writeFile(outputDir + "/daily_pnl.csv", csv);
        written++;

        return written;
    }

    /**
     * Generate the monthly volume report.
     */
    public int generateMonthlyVolumeReport(String outputDir) throws Exception {
        log.info("Generating monthly volume report...");
        List data = dao.getMonthlyVolumeReport();

        String[] columns = {"MONTH", "TOTAL_ORDERS", "TOTAL_VOLUME", "TOTAL_VALUE"};
        String[] headers = {"Month", "Total Orders", "Total Volume", "Total Value ($)"};

        int written = 0;
        String html = ReportTemplateEngine.generateHtmlReport("Monthly Trade Volume", columns, headers, data);
        writeFile(outputDir + "/monthly_volume.html", html);
        written++;

        String csv = ReportTemplateEngine.generateCsvReport(columns, headers, data);
        writeFile(outputDir + "/monthly_volume.csv", csv);
        written++;

        return written;
    }

    /**
     * Generate the billing summary report.
     */
    public int generateBillingReport(String outputDir) throws Exception {
        log.info("Generating billing report...");
        List data = dao.getBillingSummary();

        String[] columns = {"CLIENT_ID", "TOTAL_GROSS", "TOTAL_COMMISSION", "TOTAL_NET", "ENTRY_COUNT"};
        String[] headers = {"Client ID", "Gross Amount ($)", "Commission ($)", "Net Amount ($)", "Entries"};

        int written = 0;
        String html = ReportTemplateEngine.generateHtmlReport("Billing Summary", columns, headers, data);
        writeFile(outputDir + "/billing_summary.html", html);
        written++;

        String csv = ReportTemplateEngine.generateCsvReport(columns, headers, data);
        writeFile(outputDir + "/billing_summary.csv", csv);
        written++;

        return written;
    }

    /**
     * Generate the settlement summary report.
     */
    public int generateSettlementReport(String outputDir) throws Exception {
        log.info("Generating settlement report...");
        List data = dao.getSettlementSummary();

        String[] columns = {"STATUS", "RECORD_COUNT", "TOTAL_AMOUNT", "TOTAL_COMMISSION"};
        String[] headers = {"Status", "Record Count", "Total Amount ($)", "Commission ($)"};

        int written = 0;
        String html = ReportTemplateEngine.generateHtmlReport("Settlement Summary", columns, headers, data);
        writeFile(outputDir + "/settlement_summary.html", html);
        written++;

        String csv = ReportTemplateEngine.generateCsvReport(columns, headers, data);
        writeFile(outputDir + "/settlement_summary.csv", csv);
        written++;

        return written;
    }

    /**
     * Write a string to a file.
     */
    private void writeFile(String path, String content) throws Exception {
        FileWriter fw = null;
        try {
            fw = new FileWriter(path);
            fw.write(content);
            log.info("Wrote report file: " + path);
        } finally {
            if (fw != null) {
                try { fw.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }
}
