package com.bigcorp.connector.spi;

import com.bigcorp.connector.ConnectorException;

import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration for the Mainframe Resource Adapter.
 * Modeled after the ra.xml deployment descriptor properties
 * from the JCA 1.0 / 1.5 spec.
 *
 * Reads from connector/config/ra.properties (NOT from the
 * existing config/ directory — this is the vendor's own config
 * convention, kept separate from the BigCorp config structure).
 *
 * @author Vendor Integration Team (Apex Consulting)
 * @since connector-1.0
 */
public class ResourceAdapterConfig {

    private static final String CONFIG_FILE = "ra.properties";

    // Defaults for embedded/demo mode (no real mainframe)
    private static final String DEFAULT_EIS_HOST = "localhost";
    private static final int DEFAULT_EIS_PORT = 3270;
    private static final String DEFAULT_TP_NAME = "ACCT-INQ";
    private static final int DEFAULT_TIMEOUT_MS = 3000;
    private static final boolean DEFAULT_ENABLED = true;

    private String eisHost;
    private int eisPort;
    private String transactionProgram;
    private int connectionTimeoutMs;
    private boolean enabled;

    public ResourceAdapterConfig() {
        this.eisHost = DEFAULT_EIS_HOST;
        this.eisPort = DEFAULT_EIS_PORT;
        this.transactionProgram = DEFAULT_TP_NAME;
        this.connectionTimeoutMs = DEFAULT_TIMEOUT_MS;
        this.enabled = DEFAULT_ENABLED;
    }

    /**
     * Load config from ra.properties on the classpath.
     * Falls back to defaults if the file is not found.
     */
    public static ResourceAdapterConfig load() throws ConnectorException {
        ResourceAdapterConfig cfg = new ResourceAdapterConfig();

        try {
            Properties props = new Properties();
            InputStream is = ResourceAdapterConfig.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE);

            if (is != null) {
                props.load(is);
                is.close();

                cfg.eisHost = props.getProperty("eis.host", DEFAULT_EIS_HOST);
                cfg.eisPort = Integer.parseInt(
                    props.getProperty("eis.port", String.valueOf(DEFAULT_EIS_PORT)));
                cfg.transactionProgram = props.getProperty(
                    "eis.transaction.program", DEFAULT_TP_NAME);
                cfg.connectionTimeoutMs = Integer.parseInt(
                    props.getProperty("eis.connection.timeout.ms",
                        String.valueOf(DEFAULT_TIMEOUT_MS)));
                cfg.enabled = Boolean.valueOf(
                    props.getProperty("eis.enabled", "true")).booleanValue();

                System.out.println("[CONNECTOR] Loaded RA config from " + CONFIG_FILE
                    + ": host=" + cfg.eisHost + " port=" + cfg.eisPort
                    + " tp=" + cfg.transactionProgram + " enabled=" + cfg.enabled);
            } else {
                System.out.println("[CONNECTOR] " + CONFIG_FILE
                    + " not found on classpath, using defaults"
                    + " (host=" + cfg.eisHost + " port=" + cfg.eisPort + ")");
            }
        } catch (Exception e) {
            System.err.println("WARN: Failed to load RA config: " + e.getMessage()
                + " — using defaults");
        }

        return cfg;
    }

    public String getEisHost() { return eisHost; }
    public void setEisHost(String eisHost) { this.eisHost = eisHost; }

    public int getEisPort() { return eisPort; }
    public void setEisPort(int eisPort) { this.eisPort = eisPort; }

    public String getTransactionProgram() { return transactionProgram; }
    public void setTransactionProgram(String tp) { this.transactionProgram = tp; }

    public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(int ms) { this.connectionTimeoutMs = ms; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
