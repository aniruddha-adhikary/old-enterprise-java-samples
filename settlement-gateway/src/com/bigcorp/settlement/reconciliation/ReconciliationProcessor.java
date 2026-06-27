package com.bigcorp.settlement.reconciliation;

import com.bigcorp.common.model.SettlementRecord;
import com.bigcorp.settlement.dao.SettlementDAO;
import com.bigcorp.settlement.sftp.SftpPoller;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Processes inbound reconciliation files from the clearinghouse.
 *
 * The clearinghouse drops reconciliation files on their SFTP server
 * in the /outgoing/ directory. We poll for them, download, parse,
 * and update settlement record statuses accordingly.
 *
 * Reconciliation file formats:
 *   - XML (.xml): new format (2001+) and old format (pre-2001)
 *   - Fixed-width (.dat): COBOL-style flat file from the mainframe
 *
 * The .dat format was "temporary" - the clearinghouse was going to
 * switch to XML-only. That was in 1999. They still send .dat files.
 *
 * Flow:
 *   1. SftpPoller downloads files from clearinghouse SFTP (or local dir in dev mode)
 *   2. This processor identifies file type (.xml or .dat)
 *   3. XML files are handled by SftpPoller.processReconciliationFile() (existing code)
 *   4. DAT files are parsed by DatReconciliationParser
 *   5. Parsed entries update SettlementRecord statuses via SettlementDAO
 *   6. Files are moved to the "processed" directory
 *
 * @author Dave
 * @since 1.3
 */
public class ReconciliationProcessor {

    private SftpPoller poller;
    private SettlementDAO dao;
    private DatReconciliationParser datParser;

    /** Status code mapping from clearinghouse .dat format to our internal statuses */
    private static final String DAT_STATUS_CONFIRMED = "CONF";
    private static final String DAT_STATUS_REJECTED = "REJC";
    private static final String DAT_STATUS_DISCREPANCY = "DISC";
    private static final String DAT_STATUS_PENDING = "PEND";

    public ReconciliationProcessor() {
        this.poller = new SftpPoller();
        this.dao = new SettlementDAO();
        this.datParser = new DatReconciliationParser();
    }

    /**
     * Constructor for dependency injection (used in testing).
     * The architecture team would call this "Inversion of Control"
     * but we just call it "passing stuff in."
     */
    public ReconciliationProcessor(SftpPoller poller, SettlementDAO dao) {
        this.poller = poller;
        this.dao = dao;
        this.datParser = new DatReconciliationParser();
    }

    /**
     * Process all available reconciliation files.
     * 
     * Polls for new files, parses them, and updates settlement records.
     * Returns the total number of records processed across all files.
     *
     * @return number of settlement records updated
     */
    public int processInbound() {
        int totalProcessed = 0;

        System.out.println("=== Reconciliation Processor: starting inbound processing ===");
        System.out.println("  Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        // poll for available files
        List files = poller.pollForFiles();

        if (files == null || files.size() == 0) {
            System.out.println("  No reconciliation files found.");
            return 0;
        }

        System.out.println("  Found " + files.size() + " file(s) to process.");

        for (int i = 0; i < files.size(); i++) {
            String filePath = (String) files.get(i);
            System.out.println("  Processing file " + (i + 1) + "/" + files.size() + ": " + filePath);

            try {
                int processed;
                if (filePath.endsWith(".dat")) {
                    processed = processDatFile(filePath);
                } else if (filePath.endsWith(".xml")) {
                    // delegate XML processing to existing SftpPoller logic
                    poller.processReconciliationFile(filePath);
                    // we can't easily count XML records from here since the
                    // existing method doesn't return a count. Just estimate 1.
                    processed = 1; // approximate - see JIRA-3520 for proper counting
                } else {
                    System.err.println("  WARN: Unknown file type, skipping: " + filePath);
                    processed = 0;
                }

                totalProcessed += processed;

            } catch (Exception e) {
                System.err.println("  ERROR: Failed to process file: " + filePath);
                e.printStackTrace();
                // continue with next file - don't let one bad file stop the batch
            }
        }

        System.out.println("=== Reconciliation Processor: completed. Total records updated: " + totalProcessed + " ===");
        return totalProcessed;
    }

    /**
     * Process a single .dat reconciliation file.
     * Parses the fixed-width format and updates settlement records.
     *
     * @param filePath path to the .dat file
     * @return number of records successfully processed
     */
    private int processDatFile(String filePath) {
        List entries = datParser.parse(filePath);
        int processed = 0;

        if (entries == null || entries.size() == 0) {
            System.out.println("  No entries parsed from .dat file.");
            return 0;
        }

        System.out.println("  Parsed " + entries.size() + " entries from .dat file.");

        for (int i = 0; i < entries.size(); i++) {
            ReconciliationEntry entry = (ReconciliationEntry) entries.get(i);

            String recordId = entry.getRecordId();
            String rawStatus = entry.getStatus();

            if (recordId == null || recordId.length() == 0) {
                System.err.println("  WARN: Skipping entry with no record ID at position " + i);
                continue;
            }

            // map clearinghouse status to our internal status
            String internalStatus = mapDatStatus(rawStatus);
            if (internalStatus == null) {
                System.err.println("  WARN: Unknown DAT status '" + rawStatus + "' for record " + recordId + ", skipping.");
                continue;
            }

            // skip PENDING entries - clearinghouse hasn't finished processing yet
            if (DAT_STATUS_PENDING.equals(rawStatus)) {
                System.out.println("  Skipping PENDING record " + recordId + " (not yet processed by clearinghouse)");
                continue;
            }

            // update settlement record status
            dao.updateSettlementStatus(recordId, internalStatus);
            System.out.println("  Updated record " + recordId + " -> " + internalStatus
                    + " (DAT format, code=" + rawStatus + ")");

            // if there's a reason code for rejections/discrepancies, log it
            if (entry.getReasonCode() != null && entry.getReasonCode().length() > 0) {
                System.out.println("    Reason: " + entry.getReasonCode());
                // TODO: store reason code somewhere (JIRA-3522)
                // The SettlementRecord model doesn't have a reasonCode field.
                // Dave says we should add one but the DBA is on vacation.
            }

            processed++;
        }

        // move file to processed directory
        moveToProcessed(filePath);

        return processed;
    }

    /**
     * Map clearinghouse .dat status code to our internal SettlementRecord status.
     *
     * Clearinghouse codes:
     *   CONF -> RECONCILED (successfully settled)
     *   REJC -> DISCREPANCY (rejected by clearinghouse)
     *   DISC -> DISCREPANCY (amount/detail mismatch)
     *   PEND -> (no mapping - skip these records)
     */
    private String mapDatStatus(String datStatus) {
        if (datStatus == null) {
            return null;
        }

        if (DAT_STATUS_CONFIRMED.equals(datStatus)) {
            return SettlementRecord.STATUS_RECONCILED;
        } else if (DAT_STATUS_REJECTED.equals(datStatus) || DAT_STATUS_DISCREPANCY.equals(datStatus)) {
            return SettlementRecord.STATUS_DISCREPANCY;
        } else if (DAT_STATUS_PENDING.equals(datStatus)) {
            // caller should skip these
            return SettlementRecord.STATUS_UPLOADED; // keep current status
        }

        return null; // unknown code
    }

    /**
     * Move processed file to the processed directory.
     * (Duplicate of SftpPoller's moveToProcessed but we can't call that one
     * because it's private. Refactoring is on the backlog - JIRA-3525.)
     */
    private void moveToProcessed(String filePath) {
        try {
            File processedDir = new File("./sftp-root/processed/");
            if (!processedDir.exists()) {
                processedDir.mkdirs();
            }

            File sourceFile = new File(filePath);
            File destFile = new File(processedDir, sourceFile.getName());

            // if file already exists in processed dir (e.g. re-run), add timestamp
            if (destFile.exists()) {
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                destFile = new File(processedDir, timestamp + "_" + sourceFile.getName());
            }

            boolean moved = sourceFile.renameTo(destFile);
            if (!moved) {
                System.err.println("WARN: Could not move file to processed dir: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("WARN: Error moving file to processed: " + e.getMessage());
        }
    }

    /**
     * Generate a sample reconciliation file for testing.
     * 
     * Because the clearinghouse test environment is always down
     * (they reboot it every Friday at 3pm and sometimes forget to
     * turn it back on), we need to be able to generate our own
     * test files.
     *
     * @param filePath where to write the test file
     * @param recordIds array of settlement record IDs to include
     * @param format "xml" or "dat"
     */
    public static void generateTestReconciliationFile(String filePath, String[] recordIds, String format) {
        try {
            if ("dat".equalsIgnoreCase(format)) {
                generateTestDatFile(filePath, recordIds);
            } else {
                generateTestXmlFile(filePath, recordIds);
            }
            System.out.println("Generated test reconciliation file: " + filePath + " (" + format + " format)");
        } catch (Exception e) {
            System.err.println("ERROR: Failed to generate test reconciliation file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generate a .dat format test file.
     * All records are marked as CONF (confirmed) for happy-path testing.
     */
    private static void generateTestDatFile(String filePath, String[] recordIds) throws Exception {
        // ensure parent directory exists
        File parent = new File(filePath).getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        PrintWriter writer = new PrintWriter(new FileWriter(filePath));

        // write header
        writer.println(padRight("HDR", 80));

        // write data records
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
        for (int i = 0; i < recordIds.length; i++) {
            StringBuffer line = new StringBuffer();

            // positions 1-20: Record ID (left-padded with spaces to 20 chars)
            line.append(padRight(recordIds[i], 20));

            // positions 21-30: External Reference
            line.append(padRight("EXT" + (i + 1), 10));

            // positions 31-40: Status Code (CONF for test files)
            line.append(padRight("CONF", 10));

            // positions 41-50: Amount (implied 2 decimals, right-justified)
            line.append(padLeft("0000012875", 10)); // 128.75

            // positions 51-58: Date (YYYYMMDD)
            line.append(padRight(dateStr, 8));

            // positions 59-78: Reason Code (blank for confirmed)
            line.append(padRight("", 20));

            writer.println(line.toString());
        }

        // write trailer with record count
        String countStr = String.valueOf(recordIds.length);
        writer.println("TRL" + padLeft(countStr, 10));

        writer.flush();
        writer.close();
    }

    /**
     * Generate an XML format test file (new format, 2001+).
     * All records are marked as CONFIRMED for happy-path testing.
     */
    private static void generateTestXmlFile(String filePath, String[] recordIds) throws Exception {
        // ensure parent directory exists
        File parent = new File(filePath).getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        PrintWriter writer = new PrintWriter(new FileWriter(filePath));
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.println("<reconciliationBatch>");
        writer.println("  <batchDate>" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "</batchDate>");
        writer.println("  <recordCount>" + recordIds.length + "</recordCount>");

        for (int i = 0; i < recordIds.length; i++) {
            writer.println("  <record>");
            writer.println("    <recordId>" + recordIds[i] + "</recordId>");
            writer.println("    <status>CONFIRMED</status>");
            writer.println("    <externalRef>EXT-" + (i + 1) + "</externalRef>");
            writer.println("    <amount>12875.00</amount>");
            writer.println("  </record>");
        }

        writer.println("</reconciliationBatch>");
        writer.flush();
        writer.close();
    }

    // --- String padding utilities ---
    // (Yes, we could use String.format() or a library. No, we won't.
    //  This code was written in 2000 and "it works.")

    private static String padRight(String str, int length) {
        if (str == null) {
            str = "";
        }
        StringBuffer sb = new StringBuffer(str);
        while (sb.length() < length) {
            sb.append(' ');
        }
        return sb.substring(0, length);
    }

    private static String padLeft(String str, int length) {
        if (str == null) {
            str = "";
        }
        StringBuffer sb = new StringBuffer();
        while (sb.length() + str.length() < length) {
            sb.append(' ');
        }
        sb.append(str);
        return sb.substring(0, length);
    }
}
