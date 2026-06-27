package com.bigcorp.settlement.sftp;

import com.bigcorp.common.model.SettlementRecord;
import com.bigcorp.settlement.dao.SettlementDAO;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * Polls the clearinghouse SFTP server for reconciliation files.
 * 
 * The clearinghouse sends back reconciliation files (.xml or .dat) that 
 * confirm whether our settlement records were processed successfully.
 * 
 * NOTE: The reconciliation format was changed in 2001 when they "upgraded"
 * their system. The old format had status codes as numbers (1=OK, 2=FAIL),
 * the new format uses strings ("CONFIRMED", "REJECTED"). We have to support
 * both because they still send old-format files for records that were 
 * originally submitted before the cutover. This was extremely painful to 
 * debug and the spec they sent for the new format had typos.
 * 
 * @author Dave (settlements team), with "help" from the clearinghouse tech team
 * @since 1.2
 */
public class SftpPoller {

    private String host;
    private int port;
    private String username;
    private String password;
    private String remoteInboundDir;
    private boolean sftpEnabled;

    private static final int CONNECT_TIMEOUT = 30000;
    private static final String LOCAL_INBOUND_DIR = "./sftp-root/inbound/";
    private static final String LOCAL_PROCESSED_DIR = "./sftp-root/processed/";

    private SettlementDAO dao;

    public SftpPoller() {
        this.dao = new SettlementDAO();
        loadConfig();
    }

    private void loadConfig() {
        try {
            Properties props = new Properties();
            InputStream is = getClass().getClassLoader().getResourceAsStream("settlement.properties");
            if (is != null) {
                props.load(is);
                is.close();

                host = props.getProperty("sftp.host", "");
                String portStr = props.getProperty("sftp.port", "22");
                port = Integer.parseInt(portStr);
                username = props.getProperty("sftp.username", "");
                password = props.getProperty("sftp.password", "");
                remoteInboundDir = props.getProperty("sftp.remote.inbound.dir", "/outgoing/");

                sftpEnabled = (host != null && host.length() > 0);
            } else {
                sftpEnabled = false;
            }
        } catch (Exception e) {
            sftpEnabled = false;
            System.err.println("WARN: Could not load SFTP config for poller: " + e.getMessage());
        }
    }

    /**
     * Poll for new reconciliation files.
     * Returns a list of local file paths for downloaded files.
     */
    public List pollForFiles() {
        if (!sftpEnabled) {
            return pollLocalDirectory();
        }

        List downloadedFiles = new ArrayList();
        Session session = null;
        Channel channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect(CONNECT_TIMEOUT);
            channel = session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT);
            ChannelSftp sftpChannel = (ChannelSftp) channel;

            sftpChannel.cd(remoteInboundDir);
            Vector files = sftpChannel.ls("*");

            for (int i = 0; i < files.size(); i++) {
                ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) files.get(i);
                String filename = entry.getFilename();

                // only process .xml and .dat files
                if (filename.endsWith(".xml") || filename.endsWith(".dat")) {
                    String localPath = LOCAL_INBOUND_DIR + filename;

                    // ensure local directory exists
                    File localDir = new File(LOCAL_INBOUND_DIR);
                    if (!localDir.exists()) {
                        localDir.mkdirs();
                    }

                    // download file
                    FileOutputStream fos = new FileOutputStream(localPath);
                    sftpChannel.get(filename, fos);
                    fos.close();

                    // remove from remote (so we don't process again)
                    sftpChannel.rm(filename);

                    downloadedFiles.add(localPath);
                    System.out.println("Downloaded reconciliation file: " + filename);
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR: SFTP poll failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }

        return downloadedFiles;
    }

    /**
     * Fallback: poll local inbound directory for reconciliation files (dev mode).
     */
    private List pollLocalDirectory() {
        List files = new ArrayList();
        File inboundDir = new File(LOCAL_INBOUND_DIR);

        if (!inboundDir.exists()) {
            inboundDir.mkdirs();
            return files;
        }

        File[] found = inboundDir.listFiles();
        if (found != null) {
            for (int i = 0; i < found.length; i++) {
                String name = found[i].getName();
                if (name.endsWith(".xml") || name.endsWith(".dat")) {
                    files.add(found[i].getAbsolutePath());
                }
            }
        }

        return files;
    }

    /**
     * Process a reconciliation file from the clearinghouse.
     * Updates settlement record statuses in the database.
     * 
     * Handles both old format (pre-2001) and new format (post-2001).
     * Old format: numeric status codes (1=confirmed, 2=rejected/discrepancy)
     * New format: string status values ("CONFIRMED", "REJECTED", "DISCREPANCY")
     */
    public void processReconciliationFile(String filePath) {
        System.out.println("Processing reconciliation file: " + filePath);

        try {
            if (filePath.endsWith(".xml")) {
                processXmlReconciliation(filePath);
            } else if (filePath.endsWith(".dat")) {
                // .dat files use the old fixed-width format
                // TODO: implement .dat reconciliation parsing (JIRA-3455)
                // For now we only handle XML reconciliation files
                System.out.println("WARN: .dat reconciliation parsing not yet implemented, skipping: " + filePath);
            }

            // move to processed directory
            moveToProcessed(filePath);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to process reconciliation file: " + filePath);
            e.printStackTrace();
        }
    }

    /**
     * Parse XML reconciliation file.
     * Supports both old format (2000-2001) and new format (2001+).
     */
    private void processXmlReconciliation(String filePath) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbFactory.newDocumentBuilder();
        Document doc = builder.parse(new File(filePath));

        Element root = doc.getDocumentElement();
        String rootTag = root.getTagName();

        // detect format version
        // old format: <reconciliation> with <item> elements and numeric status
        // new format: <reconciliationBatch> with <record> elements and string status
        if ("reconciliation".equals(rootTag)) {
            // old format (pre-2001)
            processOldFormatReconciliation(root);
        } else if ("reconciliationBatch".equals(rootTag)) {
            // new format (2001+)
            processNewFormatReconciliation(root);
        } else {
            // try to handle it as new format anyway
            // (the clearinghouse once sent a file with root tag "reconBatch" by mistake)
            System.out.println("WARN: Unrecognized reconciliation root tag: " + rootTag + ", attempting new format parse");
            processNewFormatReconciliation(root);
        }
    }

    /**
     * Process old format reconciliation (pre-2001).
     * Status codes: 1 = confirmed, 2 = discrepancy, anything else = unknown
     */
    private void processOldFormatReconciliation(Element root) {
        NodeList items = root.getElementsByTagName("item");

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String recordId = getElementText(item, "ref");
            String statusCode = getElementText(item, "code");

            if (recordId != null && statusCode != null) {
                String newStatus;
                if ("1".equals(statusCode)) {
                    newStatus = SettlementRecord.STATUS_RECONCILED;
                } else if ("2".equals(statusCode)) {
                    newStatus = SettlementRecord.STATUS_DISCREPANCY;
                } else {
                    // unknown code - log and skip
                    System.err.println("WARN: Unknown reconciliation code '" + statusCode + "' for record " + recordId);
                    continue;
                }

                dao.updateSettlementStatus(recordId, newStatus);
                System.out.println("  Updated record " + recordId + " -> " + newStatus + " (old format, code=" + statusCode + ")");
            }
        }
    }

    /**
     * Process new format reconciliation (2001+).
     * Status values: "CONFIRMED", "REJECTED", "DISCREPANCY"
     */
    private void processNewFormatReconciliation(Element root) {
        NodeList records = root.getElementsByTagName("record");

        for (int i = 0; i < records.getLength(); i++) {
            Element record = (Element) records.item(i);
            String recordId = getElementText(record, "recordId");
            String statusStr = getElementText(record, "status");
            String externalRef = getElementText(record, "externalRef");

            if (recordId != null && statusStr != null) {
                String newStatus;
                if ("CONFIRMED".equals(statusStr)) {
                    newStatus = SettlementRecord.STATUS_RECONCILED;
                } else if ("REJECTED".equals(statusStr) || "DISCREPANCY".equals(statusStr)) {
                    newStatus = SettlementRecord.STATUS_DISCREPANCY;
                } else {
                    // the clearinghouse sometimes sends "PENDING" which means they haven't
                    // processed it yet - just skip these
                    System.out.println("  Skipping record " + recordId + " with status: " + statusStr);
                    continue;
                }

                dao.updateSettlementStatus(recordId, newStatus);
                System.out.println("  Updated record " + recordId + " -> " + newStatus + " (new format)");

                // TODO: store externalRef if provided (JIRA-3501)
                if (externalRef != null && externalRef.length() > 0) {
                    // we should update the external_ref column but there's no method for that yet
                    System.out.println("  (externalRef: " + externalRef + " - not stored, see JIRA-3501)");
                }
            }
        }
    }

    private void moveToProcessed(String filePath) {
        try {
            File processedDir = new File(LOCAL_PROCESSED_DIR);
            if (!processedDir.exists()) {
                processedDir.mkdirs();
            }

            File sourceFile = new File(filePath);
            File destFile = new File(processedDir, sourceFile.getName());
            sourceFile.renameTo(destFile);
        } catch (Exception e) {
            System.err.println("WARN: Could not move file to processed dir: " + e.getMessage());
        }
    }

    private static String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
}
