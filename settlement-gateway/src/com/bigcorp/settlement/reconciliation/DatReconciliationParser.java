package com.bigcorp.settlement.reconciliation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the clearinghouse's COBOL-style fixed-width reconciliation files.
 *
 * Record layout (80 bytes per line, because that's a punch card width):
 *   Positions 1-20:  Record ID (left-padded with spaces)
 *   Positions 21-30: External Reference
 *   Positions 31-40: Status Code (CONF/REJC/DISC/PEND)
 *   Positions 41-50: Amount (right-justified, 2 decimal places implied)
 *   Positions 51-58: Date (YYYYMMDD)
 *   Positions 59-78: Reason Code (blank if confirmed)
 *   Position 79-80:  Line ending (CR/LF)
 *
 * Header line starts with "HDR" and trailer starts with "TRL".
 * The trailer contains a record count for validation.
 *
 * NOTE: The "spec" for this format was a faxed photocopy of a printout
 * of a mainframe screen dump. Some of the column positions were off by one.
 * Dave spent three days debugging this in 2000. The positions above are
 * the ACTUAL positions (not what the spec says).
 *
 * @author Dave (reverse-engineered from the clearinghouse spec PDF)
 * @since 1.3
 */
public class DatReconciliationParser {

    // Field positions (0-based indices into the line string)
    // The spec says 1-based but Java strings are 0-based, so subtract 1
    private static final int RECORD_ID_START = 0;
    private static final int RECORD_ID_END = 20;
    private static final int EXT_REF_START = 20;
    private static final int EXT_REF_END = 30;
    private static final int STATUS_START = 30;
    private static final int STATUS_END = 40;
    private static final int AMOUNT_START = 40;
    private static final int AMOUNT_END = 50;
    private static final int DATE_START = 50;
    private static final int DATE_END = 58;
    private static final int REASON_START = 58;
    private static final int REASON_END = 78;

    /**
     * Parse a .dat file and return list of ReconciliationEntry objects.
     * 
     * Skips the header (HDR) and trailer (TRL) lines.
     * Validates record count from trailer against actual records parsed.
     * 
     * @param filePath path to the .dat reconciliation file
     * @return list of parsed entries (empty list if file is empty or unparseable)
     */
    public List parse(String filePath) {
        List entries = new ArrayList();
        BufferedReader reader = null;
        int expectedCount = -1;

        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;

            while ((line = reader.readLine()) != null) {
                // skip empty lines (shouldn't happen but the clearinghouse
                // sometimes adds blank lines at the end)
                if (line.trim().length() == 0) {
                    continue;
                }

                // check for header
                if (line.startsWith("HDR")) {
                    // header line - contains batch info but we don't use it
                    System.out.println("  DAT parser: header found");
                    continue;
                }

                // check for trailer
                if (line.startsWith("TRL")) {
                    // trailer contains record count for validation
                    expectedCount = parseTrailerCount(line);
                    System.out.println("  DAT parser: trailer found, expected count=" + expectedCount);
                    continue;
                }

                // pad line to 80 chars if shorter (some files have truncated lines)
                while (line.length() < 80) {
                    line = line + " ";
                }

                // parse data record
                ReconciliationEntry entry = parseDataLine(line);
                if (entry != null) {
                    entries.add(entry);
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to parse .dat reconciliation file: " + filePath);
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // ignore close errors (standard practice circa 2001)
                }
            }
        }

        // validate record count if trailer was present
        if (expectedCount >= 0 && expectedCount != entries.size()) {
            System.err.println("WARN: Record count mismatch in " + filePath
                    + ": trailer says " + expectedCount + " but parsed " + entries.size()
                    + ". Processing anyway (per JIRA-3490 decision).");
            // we process anyway because the clearinghouse has a known bug where
            // the trailer count is sometimes off by one (JIRA-3490)
        }

        return entries;
    }

    /**
     * Parse a single data line from the fixed-width file.
     */
    private ReconciliationEntry parseDataLine(String line) {
        try {
            ReconciliationEntry entry = new ReconciliationEntry();

            // extract fields by position and trim whitespace
            String recordId = line.substring(RECORD_ID_START, RECORD_ID_END).trim();
            String extRef = line.substring(EXT_REF_START, EXT_REF_END).trim();
            String status = line.substring(STATUS_START, STATUS_END).trim();
            String amountStr = line.substring(AMOUNT_START, AMOUNT_END).trim();
            String date = line.substring(DATE_START, DATE_END).trim();
            String reason = line.substring(REASON_START, REASON_END).trim();

            // record ID is required
            if (recordId.length() == 0) {
                return null;
            }

            entry.setRecordId(recordId);
            entry.setExternalRef(extRef.length() > 0 ? extRef : null);
            entry.setStatus(status);
            entry.setDate(date.length() > 0 ? date : null);
            entry.setReasonCode(reason.length() > 0 ? reason : null);

            // parse amount (implied 2 decimal places: "0000012875" = 128.75)
            if (amountStr.length() > 0) {
                try {
                    // the amount field uses implied decimal (last 2 digits are cents)
                    // but sometimes they send it with an actual decimal point
                    if (amountStr.indexOf('.') >= 0) {
                        entry.setAmount(Double.parseDouble(amountStr));
                    } else {
                        long rawAmount = Long.parseLong(amountStr);
                        entry.setAmount(rawAmount / 100.0);
                    }
                } catch (NumberFormatException e) {
                    // bad amount data - set to 0 and log
                    System.err.println("WARN: Could not parse amount '" + amountStr + "' for record " + recordId);
                    entry.setAmount(0.0);
                }
            }

            return entry;

        } catch (Exception e) {
            System.err.println("WARN: Could not parse DAT line: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract the record count from the trailer line.
     * Trailer format: "TRL" followed by record count at positions 4-13.
     */
    private int parseTrailerCount(String line) {
        try {
            // pad if needed
            while (line.length() < 14) {
                line = line + " ";
            }
            String countStr = line.substring(3, 13).trim();
            if (countStr.length() > 0) {
                return Integer.parseInt(countStr);
            }
        } catch (NumberFormatException e) {
            System.err.println("WARN: Could not parse trailer record count");
        }
        return -1;
    }
}
