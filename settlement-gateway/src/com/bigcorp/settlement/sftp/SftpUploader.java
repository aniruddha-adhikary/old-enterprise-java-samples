package com.bigcorp.settlement.sftp;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Uploads settlement files to the clearinghouse via SFTP.
 * 
 * The clearinghouse runs an ancient SSH server that only supports
 * certain key exchange algorithms. If you get "Algorithm negotiation fail"
 * errors, check the JSch config below.
 * 
 * // The clearinghouse SFTP server rejects connections on Sundays for maintenance
 * 
 * @author Dave (settlements team)
 * @since 1.1
 */
public class SftpUploader {

    private String host;
    private int port;
    private String username;
    private String password;
    private String remoteDir;
    private boolean sftpEnabled;

    // connection timeout in milliseconds (clearinghouse is slow)
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int MAX_RETRIES = 1;

    // local fallback directory for dev/demo mode
    private static final String LOCAL_SFTP_ROOT = "./sftp-root/outbound/";

    public SftpUploader() {
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
                remoteDir = props.getProperty("sftp.remote.dir", "/incoming/");

                // SFTP is enabled only if host is configured
                sftpEnabled = (host != null && host.length() > 0);
            } else {
                sftpEnabled = false;
                System.out.println("WARN: settlement.properties not found, SFTP disabled (using local fallback)");
            }
        } catch (Exception e) {
            sftpEnabled = false;
            System.err.println("WARN: Could not load SFTP config: " + e.getMessage());
        }
    }

    /**
     * Upload a file to the clearinghouse SFTP server.
     * Falls back to local copy if SFTP is not configured (dev mode).
     * 
     * @param localFilePath path to the file to upload
     * @param remoteDirectory remote directory on the SFTP server (overrides config if non-null)
     */
    public void upload(String localFilePath, String remoteDirectory) {
        if (!sftpEnabled) {
            // dev mode: just copy to local sftp-root
            copyToLocalFallback(localFilePath);
            return;
        }

        String targetDir = (remoteDirectory != null && remoteDirectory.length() > 0) 
                ? remoteDirectory : this.remoteDir;

        int attempts = 0;
        boolean success = false;

        while (attempts <= MAX_RETRIES && !success) {
            attempts++;
            Session session = null;
            Channel channel = null;

            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, host, port);
                session.setPassword(password);

                // disable strict host key checking for development
                // (in production we should add the clearinghouse key to known_hosts)
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);

                session.setServerAliveInterval(15000);
                session.connect(CONNECT_TIMEOUT);

                channel = session.openChannel("sftp");
                channel.connect(CONNECT_TIMEOUT);
                ChannelSftp sftpChannel = (ChannelSftp) channel;

                // navigate to remote directory
                sftpChannel.cd(targetDir);

                // upload file
                File localFile = new File(localFilePath);
                FileInputStream fis = new FileInputStream(localFile);
                sftpChannel.put(fis, localFile.getName());
                fis.close();

                System.out.println("SFTP upload successful: " + localFile.getName() + " -> " + targetDir);
                success = true;

            } catch (Exception e) {
                System.err.println("ERROR: SFTP upload attempt " + attempts + " failed: " + e.getMessage());
                if (attempts <= MAX_RETRIES) {
                    System.out.println("Retrying SFTP upload...");
                    try { Thread.sleep(5000); } catch (InterruptedException ie) { /* ignore */ }
                } else {
                    System.err.println("ERROR: SFTP upload failed after " + attempts + " attempts, falling back to local copy");
                    e.printStackTrace();
                    copyToLocalFallback(localFilePath);
                }
            } finally {
                if (channel != null && channel.isConnected()) {
                    channel.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }
        }
    }

    /**
     * Fallback: copy file to local sftp-root directory for development/demo.
     */
    private void copyToLocalFallback(String localFilePath) {
        try {
            File destDir = new File(LOCAL_SFTP_ROOT);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            File sourceFile = new File(localFilePath);
            File destFile = new File(destDir, sourceFile.getName());

            FileInputStream fis = new FileInputStream(sourceFile);
            FileOutputStream fos = new FileOutputStream(destFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            fis.close();
            fos.close();

            System.out.println("Local SFTP fallback: copied " + sourceFile.getName() + " to " + LOCAL_SFTP_ROOT);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to copy file to local SFTP root: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
