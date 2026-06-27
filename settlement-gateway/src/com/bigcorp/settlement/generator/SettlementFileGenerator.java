package com.bigcorp.settlement.generator;

import com.bigcorp.common.model.SettlementRecord;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Generates settlement files for upload to the clearinghouse.
 * 
 * Two formats are required:
 *   1. XML format - based on the spec they faxed us in 1999
 *      (three pages, partly illegible, we did our best)
 *   2. Fixed-width flat file - because their mainframe can't parse XML
 *      (they said they'd upgrade "by Q2 2000"... still waiting)
 * 
 * The flat file format is based on a COBOL copybook they also faxed.
 * Column widths MUST NOT CHANGE or the clearinghouse rejects the whole batch.
 * 
 * @author Dave (settlements team)
 * @since 1.1
 */
public class SettlementFileGenerator {

    private static final String DATE_FORMAT = "yyyyMMdd";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    private String outputDir;

    // dead code: this was the old format before the clearinghouse changed the spec
    // private static final String OLD_RECORD_SEPARATOR = "||";
    // private static final int OLD_RECORD_ID_WIDTH = 16;

    public SettlementFileGenerator() {
        // load output directory from properties
        try {
            Properties props = new Properties();
            InputStream is = getClass().getClassLoader().getResourceAsStream("settlement.properties");
            if (is != null) {
                props.load(is);
                is.close();
                outputDir = props.getProperty("settlement.output.dir", "./sftp-outbound/");
            } else {
                outputDir = "./sftp-outbound/";
            }
        } catch (Exception e) {
            outputDir = "./sftp-outbound/";
            System.err.println("WARN: Could not load settlement.properties, using default output dir");
        }

        // ensure output directory exists
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Generate the XML settlement file.
     * 
     * Format is based on the clearinghouse spec (fax dated 1999-11-12, ref CH-SPEC-004).
     * We're not 100% sure about the namespace — their test environment accepts it
     * without one, so we leave it off.
     * 
     * @param records list of settlement records
     * @param batchId batch identifier
     * @return path to the generated file
     */
    public String generateXmlFile(List records, String batchId) {
        String filePath = outputDir + "SETTLE_" + batchId + ".xml";

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // root element
            Element root = doc.createElement("settlementBatch");
            doc.appendChild(root);

            // header
            Element header = doc.createElement("header");
            root.appendChild(header);

            Element batchIdElem = doc.createElement("batchId");
            batchIdElem.setTextContent(batchId);
            header.appendChild(batchIdElem);

            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT);
            Element dateElem = doc.createElement("generatedDate");
            dateElem.setTextContent(sdf.format(new Date()));
            header.appendChild(dateElem);

            Element countElem = doc.createElement("recordCount");
            countElem.setTextContent(String.valueOf(records.size()));
            header.appendChild(countElem);

            // detail records
            Element details = doc.createElement("records");
            root.appendChild(details);

            SimpleDateFormat dateFmt = new SimpleDateFormat(DATE_FORMAT);
            DecimalFormat amountFmt = new DecimalFormat("0.00");

            for (int i = 0; i < records.size(); i++) {
                SettlementRecord rec = (SettlementRecord) records.get(i);

                Element record = doc.createElement("record");
                details.appendChild(record);

                Element recId = doc.createElement("recordId");
                recId.setTextContent(rec.getRecordId());
                record.appendChild(recId);

                Element ordId = doc.createElement("orderId");
                ordId.setTextContent(rec.getOrderId());
                record.appendChild(ordId);

                Element clientId = doc.createElement("clientId");
                clientId.setTextContent(rec.getClientId());
                record.appendChild(clientId);

                Element symbol = doc.createElement("symbol");
                symbol.setTextContent(rec.getSymbol());
                record.appendChild(symbol);

                Element qty = doc.createElement("quantity");
                qty.setTextContent(String.valueOf(rec.getQuantity()));
                record.appendChild(qty);

                Element side = doc.createElement("side");
                side.setTextContent(rec.getSide());
                record.appendChild(side);

                Element amount = doc.createElement("amount");
                amount.setTextContent(amountFmt.format(rec.getAmount()));
                record.appendChild(amount);

                Element commission = doc.createElement("commission");
                commission.setTextContent(amountFmt.format(rec.getCommission()));
                record.appendChild(commission);

                Element tradeDate = doc.createElement("tradeDate");
                tradeDate.setTextContent(rec.getTradeDate() != null ? dateFmt.format(rec.getTradeDate()) : "");
                record.appendChild(tradeDate);

                Element settleDate = doc.createElement("settlementDate");
                settleDate.setTextContent(rec.getSettlementDate() != null ? dateFmt.format(rec.getSettlementDate()) : "");
                record.appendChild(settleDate);
            }

            // write to file
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);

            System.out.println("Generated XML settlement file: " + filePath + " (" + records.size() + " records)");
            return filePath;

        } catch (Exception e) {
            System.err.println("ERROR: Failed to generate XML settlement file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generate the fixed-width flat file for the clearinghouse mainframe.
     * 
     * Format spec (from the COBOL copybook, fax page 2 of 3):
     *   Header:  "H" + batchId(20) + date(8 yyyyMMdd) + recordCount(6 right-justified)
     *   Detail:  "D" + recordId(20) + orderId(20) + symbol(10) + side(4) 
     *            + quantity(10 right-justified) + amount(15 right-justified 2 dec) 
     *            + commission(10 right-justified)
     *   Trailer: "T" + totalAmount(20 right-justified) + totalCommission(15 right-justified) 
     *            + recordCount(6)
     * 
     * IMPORTANT: Do NOT change column widths. The clearinghouse parser reads
     * fixed positions. Last time someone changed a width (2000-03-14) it took
     * two weeks to get the file reprocessed.
     * 
     * @param records list of settlement records
     * @param batchId batch identifier
     * @return path to the generated file
     */
    public String generateFlatFile(List records, String batchId) {
        String filePath = outputDir + "SETTLE_" + batchId + ".dat";

        try {
            FileWriter writer = new FileWriter(filePath);
            StringBuffer sb = new StringBuffer();

            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            String dateStr = sdf.format(new Date());
            DecimalFormat amountFmt = new DecimalFormat("0.00");

            // Header record
            sb.append("H");
            sb.append(padRight(batchId, 20));
            sb.append(dateStr); // 8 chars
            sb.append(padLeft(String.valueOf(records.size()), 6));
            sb.append("\n");

            double totalAmount = 0.0;
            double totalCommission = 0.0;

            // Detail records
            for (int i = 0; i < records.size(); i++) {
                SettlementRecord rec = (SettlementRecord) records.get(i);

                sb.append("D");
                sb.append(padRight(rec.getRecordId(), 20));
                sb.append(padRight(rec.getOrderId(), 20));
                sb.append(padRight(rec.getSymbol(), 10));
                sb.append(padRight(rec.getSide(), 4));
                sb.append(padLeft(String.valueOf(rec.getQuantity()), 10));
                sb.append(padLeft(amountFmt.format(rec.getAmount()), 15));
                sb.append(padLeft(amountFmt.format(rec.getCommission()), 10));
                sb.append("\n");

                totalAmount += rec.getAmount();
                totalCommission += rec.getCommission();
            }

            // Trailer record
            sb.append("T");
            sb.append(padLeft(amountFmt.format(totalAmount), 20));
            sb.append(padLeft(amountFmt.format(totalCommission), 15));
            sb.append(padLeft(String.valueOf(records.size()), 6));
            sb.append("\n");

            writer.write(sb.toString());
            writer.close();

            System.out.println("Generated flat settlement file: " + filePath + " (" + records.size() + " records)");
            return filePath;

        } catch (Exception e) {
            System.err.println("ERROR: Failed to generate flat settlement file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ---- padding utilities ----
    // (these are basically what COBOL does natively... sigh)

    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        StringBuffer sb = new StringBuffer(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String padLeft(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        StringBuffer sb = new StringBuffer();
        int padding = width - s.length();
        for (int i = 0; i < padding; i++) {
            sb.append(' ');
        }
        sb.append(s);
        return sb.toString();
    }
}
