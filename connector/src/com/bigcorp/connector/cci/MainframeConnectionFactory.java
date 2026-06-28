package com.bigcorp.connector.cci;

import com.bigcorp.connector.ConnectorException;
import com.bigcorp.connector.spi.ResourceAdapterConfig;

/**
 * CCI-style connection factory for the mainframe resource adapter.
 * Modeled after javax.resource.cci.ConnectionFactory.
 *
 * In a real J2EE container, this would be registered in JNDI by the
 * deployer after configuring the .rar descriptor. Since we don't have
 * a JCA container, we instantiate it directly and feed it the
 * ResourceAdapterConfig loaded from ra.properties.
 *
 * @author Vendor Integration Team (Apex Consulting)
 * @since connector-1.0
 */
public class MainframeConnectionFactory {

    private ResourceAdapterConfig config;

    public MainframeConnectionFactory() throws ConnectorException {
        this.config = ResourceAdapterConfig.load();
    }

    public MainframeConnectionFactory(ResourceAdapterConfig config) {
        this.config = config;
    }

    public MainframeConnection getConnection() throws ConnectorException {
        System.out.println("[CONNECTOR] Opening mainframe connection to "
            + config.getEisHost() + ":" + config.getEisPort()
            + " (TP=" + config.getTransactionProgram() + ")");
        return new MainframeConnection(config);
    }

    public ResourceAdapterConfig getConfig() {
        return config;
    }
}
