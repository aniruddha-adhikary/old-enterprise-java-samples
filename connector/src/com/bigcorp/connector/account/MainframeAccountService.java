package com.bigcorp.connector.account;

import com.bigcorp.connector.ConnectorException;
import com.bigcorp.connector.cci.MainframeConnection;
import com.bigcorp.connector.cci.MainframeConnectionFactory;
import com.bigcorp.connector.cci.MainframeInteraction;

/**
 * Service facade wrapping the JCA connection factory + interaction
 * to provide a simple account verification API for the order flow.
 *
 * This is the seam that order-engine calls. It handles connection
 * lifecycle (open/close) so the caller doesn't have to deal with
 * CCI plumbing.
 *
 * @author Vendor Integration Team (Apex Consulting)
 * @since connector-1.0
 */
public class MainframeAccountService {

    private MainframeConnectionFactory connectionFactory;

    public MainframeAccountService() throws ConnectorException {
        this.connectionFactory = new MainframeConnectionFactory();
    }

    public MainframeAccountService(MainframeConnectionFactory factory) {
        this.connectionFactory = factory;
    }

    /**
     * Verify a client account against the mainframe EIS.
     * Returns the account record (credit limit, status, etc.)
     * or null if the account is not found.
     *
     * @param clientId the client identifier
     * @return AccountRecord from the mainframe (or DB fallback), or null
     */
    public AccountRecord verifyAccount(String clientId) throws ConnectorException {
        MainframeConnection conn = null;
        MainframeInteraction ixn = null;
        try {
            conn = connectionFactory.getConnection();
            ixn = conn.createInteraction();
            return ixn.executeAccountInquiry(clientId);
        } finally {
            if (ixn != null) {
                ixn.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
    }

    /**
     * Check if the connector is enabled in the RA config.
     */
    public boolean isEnabled() {
        try {
            return connectionFactory.getConfig().isEnabled();
        } catch (Exception e) {
            return false;
        }
    }
}
