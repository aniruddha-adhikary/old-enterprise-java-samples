package com.bigcorp.common.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * JDBC connection helper. Uses DriverManager directly because 
 * "connection pooling is something we'll add when we need it."
 * 
 * Configuration is read from db.properties on the classpath.
 * Falls back to HSQLDB in-memory if properties not found.
 * 
 * @author Bob
 * @since 1.0
 */
public class ConnectionHelper {

    private static String url;
    private static String username;
    private static String password;
    private static String driver;
    private static boolean initialized = false;

    /**
     * Initialize from properties file.
     * Call this once at startup. Or don't, and we'll use defaults.
     */
    public static synchronized void init() {
        if (initialized) return;

        try {
            Properties props = new Properties();
            InputStream is = ConnectionHelper.class.getClassLoader()
                    .getResourceAsStream("db.properties");

            if (is != null) {
                props.load(is);
                is.close();
                driver = props.getProperty("db.driver", "org.hsqldb.jdbc.JDBCDriver");
                url = props.getProperty("db.url", "jdbc:hsqldb:mem:bigcorpdb");
                username = props.getProperty("db.username", "sa");
                password = props.getProperty("db.password", "");
            } else {
                // properties file not found, use HSQLDB defaults
                System.out.println("WARN: db.properties not found, using HSQLDB in-memory database");
                driver = "org.hsqldb.jdbc.JDBCDriver";
                url = "jdbc:hsqldb:mem:bigcorpdb";
                username = "sa";
                password = "";
            }

            Class.forName(driver);
            initialized = true;
            System.out.println("Database initialized: " + url);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to initialize database connection: " + e.getMessage());
            e.printStackTrace();
            // fall back to HSQLDB anyway
            driver = "org.hsqldb.jdbc.JDBCDriver";
            url = "jdbc:hsqldb:mem:bigcorpdb";
            username = "sa";
            password = "";
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException("Cannot load HSQLDB driver", ex);
            }
            initialized = true;
        }
    }

    /**
     * Get a database connection.
     * Caller is responsible for closing it. Yes, we know about try-with-resources.
     * No, this was written before that existed.
     */
    public static Connection getConnection() {
        if (!initialized) {
            init();
        }
        try {
            return DriverManager.getConnection(url, username, password);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to get database connection: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database connection failed", e);
        }
    }

    /**
     * Close a connection, ignoring errors.
     */
    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * Close a statement, ignoring errors.
     */
    public static void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * Close a result set, ignoring errors.
     */
    public static void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
