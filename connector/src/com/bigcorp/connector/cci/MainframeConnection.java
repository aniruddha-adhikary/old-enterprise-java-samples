package com.bigcorp.connector.cci;

import com.bigcorp.connector.ConnectorException;
import com.bigcorp.connector.spi.ResourceAdapterConfig;

/**
 * CCI-style connection to the mainframe EIS.
 * Modeled after javax.resource.cci.Connection.
 *
 * Represents an open "session" to the CICS transaction server.
 * In the real deployment this would hold a TCP socket to the
 * mainframe via SNA LU6.2 or TCP/IP CICS Gateway. Here it just
 * holds the config so MainframeInteraction can use it.
 *
 * @author Vendor Integration Team (Apex Consulting)
 * @since connector-1.0
 */
public class MainframeConnection {

    private ResourceAdapterConfig config;
    private boolean closed = false;

    MainframeConnection(ResourceAdapterConfig config) {
        this.config = config;
    }

    public MainframeInteraction createInteraction() throws ConnectorException {
        if (closed) {
            throw new ConnectorException("Connection is closed", "CONN-001");
        }
        return new MainframeInteraction(this);
    }

    public ResourceAdapterConfig getConfig() {
        return config;
    }

    public void close() {
        this.closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
