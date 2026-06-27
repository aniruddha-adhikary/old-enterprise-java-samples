package com.bigcorp.reporting.config;

/**
 * Configuration holder for reporting module.
 * 
 * We use our own config rather than whatever the main app uses.
 * The reporting team manages this separately from the trading config.
 * 
 * @author contractor (reporting team)
 * @since 2013-Q2
 */
public class ReportConfig {

    // Database settings — we read from the same DB as everyone else
    // but maintain our own connection params just in case they change
    private static final String DEFAULT_DB_DRIVER = "org.hsqldb.jdbc.JDBCDriver";
    private static final String DEFAULT_DB_URL = "jdbc:hsqldb:mem:bigcorpdb";
    private static final String DEFAULT_DB_USER = "sa";
    private static final String DEFAULT_DB_PASS = "";

    // Output directory for generated reports
    private static final String DEFAULT_OUTPUT_DIR = "./reports-output/";

    // Report date format (different from the rest of the app because
    // the reporting team prefers DD/MM/YYYY, not the US format)
    public static final String DATE_FORMAT = "dd/MM/yyyy";
    public static final String TIMESTAMP_FORMAT = "dd/MM/yyyy HH:mm:ss";

    // CSV delimiter
    public static final String CSV_DELIMITER = ",";

    // HTML report settings
    public static final String HTML_CHARSET = "ISO-8859-1";
    public static final String HTML_TITLE_PREFIX = "BigCorp Report - ";

    public static String getDbDriver() {
        return System.getProperty("reporting.db.driver", DEFAULT_DB_DRIVER);
    }

    public static String getDbUrl() {
        return System.getProperty("reporting.db.url", DEFAULT_DB_URL);
    }

    public static String getDbUser() {
        return System.getProperty("reporting.db.user", DEFAULT_DB_USER);
    }

    public static String getDbPass() {
        return System.getProperty("reporting.db.pass", DEFAULT_DB_PASS);
    }

    public static String getOutputDir() {
        return System.getProperty("reporting.output.dir", DEFAULT_OUTPUT_DIR);
    }
}
