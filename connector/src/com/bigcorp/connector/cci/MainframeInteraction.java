package com.bigcorp.connector.cci;

import com.bigcorp.connector.ConnectorException;
import com.bigcorp.connector.account.AccountRecord;
import com.bigcorp.connector.spi.ResourceAdapterConfig;
import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * CCI-style interaction for executing commands against the mainframe EIS.
 * Modeled after javax.resource.cci.Interaction.
 *
 * Executes a "transaction program" on the mainframe. The real impl
 * would send a COMMAREA via the CICS Transaction Gateway; this one
 * SIMULATES the mainframe by trying a TCP connect first (which will
 * fail in demo/embedded mode) and then falling back to reading the
 * same data from the CLIENTS table via JDBC.
 *
 * @author Vendor Integration Team (Apex Consulting)
 * @since connector-1.0
 */
public class MainframeInteraction {

    private MainframeConnection connection;
    private boolean closed = false;

    MainframeInteraction(MainframeConnection connection) {
        this.connection = connection;
    }

    /**
     * Execute the ACCT-INQ transaction program to retrieve account data.
     * Tries the mainframe host first; falls back to CLIENTS table via JDBC.
     *
     * @param clientId the client/account identifier (maps to ACCT-NBR on the mainframe)
     * @return AccountRecord with credit limit and account status, or null if not found
     */
    public AccountRecord executeAccountInquiry(String clientId) throws ConnectorException {
        if (closed) {
            throw new ConnectorException("Interaction is closed", "IXNR-001");
        }

        ResourceAdapterConfig cfg = connection.getConfig();

        // Step 1: Try the real mainframe connection
        try {
            return callMainframeEIS(clientId, cfg);
        } catch (Exception e) {
            System.err.println("WARN: Mainframe EIS connection failed for account "
                + clientId + ", falling back to DB lookup: " + e.getMessage());
        }

        // Step 2: Fallback — read from CLIENTS table via JDBC
        // (same pattern as PricingServiceClient.getQuoteFromDatabase)
        return lookupAccountFromDatabase(clientId);
    }

    /**
     * Attempt to connect to the CICS mainframe host.
     * In the embedded/demo environment this will always fail because
     * there is no mainframe running on localhost:3270.
     */
    private AccountRecord callMainframeEIS(String clientId, ResourceAdapterConfig cfg)
            throws Exception {
        // Try a TCP socket connect to the EIS host to see if the mainframe is reachable
        java.net.Socket socket = null;
        try {
            socket = new java.net.Socket();
            socket.connect(
                new java.net.InetSocketAddress(cfg.getEisHost(), cfg.getEisPort()),
                cfg.getConnectionTimeoutMs()
            );
            // If we got here, the mainframe is reachable.
            // In a real implementation, we'd send a COMMAREA request via the
            // CICS Transaction Gateway protocol. For now, we just throw
            // because we can't actually parse CICS responses.
            throw new ConnectorException(
                "CICS Gateway protocol not implemented — simulation only",
                "CICS-NOPROTOCOL"
            );
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) { }
            }
        }
    }

    /**
     * Fallback: read credit limit and account status from the CLIENTS table.
     * Maps CLIENTS columns to the AccountRecord value object.
     *
     * This mirrors the PricingServiceClient.getQuoteFromDatabase() pattern:
     * try the remote service, fall back to local DB.
     */
    private AccountRecord lookupAccountFromDatabase(String clientId) throws ConnectorException {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            // NOTE: SQL uses string concatenation, matching the existing DAO style
            String sql = "SELECT CLIENT_ID, MAX_ORDER_VALUE, ACTIVE, KILL_SWITCH "
                + "FROM CLIENTS WHERE CLIENT_ID = '" + clientId + "'";
            rs = stmt.executeQuery(sql);

            if (rs.next()) {
                AccountRecord record = new AccountRecord();
                record.setAccountNumber(rs.getString("CLIENT_ID"));
                record.setCreditLimit(rs.getDouble("MAX_ORDER_VALUE"));

                // Map DB columns to mainframe-style account status
                int active = rs.getInt("ACTIVE");
                String killSwitch = rs.getString("KILL_SWITCH");

                if ("Y".equals(killSwitch)) {
                    record.setAccountStatus(AccountRecord.STATUS_SUSPENDED);
                } else if (active == 1) {
                    record.setAccountStatus(AccountRecord.STATUS_ACTIVE);
                } else {
                    record.setAccountStatus(AccountRecord.STATUS_CLOSED);
                }

                // No settlement date in CLIENTS table — mainframe would have this
                record.setLastSettlementDate("N/A");

                System.out.println("[CONNECTOR] Account lookup via DB fallback: " + record);
                return record;
            }

            return null;

        } catch (Exception e) {
            throw new ConnectorException(
                "DB fallback failed for account " + clientId, "DB-001", e
            );
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    public void close() {
        this.closed = true;
    }
}
