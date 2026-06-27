package com.bigcorp.common.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.Vector;

/**
 * Hand-rolled connection pool.
 *
 * We wrote our own because:
 * 1. The app server's pool "didn't work right" (Bob, 2000)
 * 2. Jakarta DBCP wasn't released yet when we started
 * 3. "How hard can it be?" (Bob, 2000 -- famous last words)
 *
 * Known issues:
 * - No connection validation (stale connections cause ORA-03113)
 * - No max wait timeout (threads can block forever)
 * - Connections that are never returned leak silently
 * - The pool grows but never shrinks
 * - Thread safety is "probably fine" per Bob
 * - No idle connection eviction (the DBA complains about ghost sessions)
 * - The pool counter goes negative sometimes but "it still works"
 *
 * JIRA-2001: Replace with DBCP when we upgrade to Java 1.4
 * (We are now on Java 1.4 and haven't replaced it)
 *
 * JIRA-2287: Investigate ORA-03113 errors during overnight batch
 * (Turned out to be stale pooled connections. "Fixed" by restarting the JVM.)
 *
 * @author Bob
 * @since 1.0
 */
public class ConnectionPool {

    private static ConnectionPool instance;

    // Vector for "thread safety" - Bob insisted on Vector over ArrayList.
    // "ArrayList is NOT synchronized. Do you want data corruption?" - Bob
    private Vector availableConnections;
    private Vector usedConnections;

    private String url;
    private String username;
    private String password;
    private String driver;

    // Defaults. These were "tuned" by Bob setting them to round numbers.
    // "5 initial connections should be enough for anybody" - Bob, 2000
    private int initialSize = 5;
    private int maxSize = 20;

    // Track if we've been initialized. Separate from ConnectionHelper
    // because "the pool has its own lifecycle" per the design doc.
    private boolean initialized = false;

    // Total connections ever created (for debugging that connection leak)
    private int totalCreated = 0;

    /**
     * Private constructor - Singleton.
     * Bob: "There should be exactly one pool. That's the whole point."
     * Dave: "What about testing?" Bob: "What about it?"
     */
    private ConnectionPool() {
        availableConnections = new Vector();
        usedConnections = new Vector();
    }

    /**
     * Get the singleton instance.
     * If the pool isn't initialized yet, it initializes itself from db.properties.
     * Same properties file as ConnectionHelper uses.
     */
    public static synchronized ConnectionPool getInstance() {
        if (instance == null) {
            instance = new ConnectionPool();
            instance.initializePool();
        }
        return instance;
    }

    /**
     * Get a connection from the pool.
     * 
     * If there's an available connection, takes it from the available list.
     * If not, creates a new one (up to maxSize).
     * If we're at maxSize... well, Bob's code just creates another one anyway
     * because "what else are we gonna do, throw an exception?"
     * 
     * The caller MUST call releaseConnection() when done. If they don't,
     * the connection leaks. We have no leak detection. JIRA-2156.
     */
    public synchronized Connection getConnection() {
        if (!initialized) {
            initializePool();
        }

        Connection conn = null;

        if (availableConnections.size() > 0) {
            // grab the last one (LIFO because... Bob didn't think about it)
            conn = (Connection) availableConnections.remove(availableConnections.size() - 1);

            // TODO: validate the connection before returning it (JIRA-2287)
            // We should do conn.isValid(5) or at least SELECT 1 FROM DUAL
            // but "it's too slow" per Bob. Meanwhile ORA-03113 errors continue.
            try {
                if (conn.isClosed()) {
                    // stale connection, create a new one
                    System.out.println("WARN: Discarded closed connection from pool. Creating new one.");
                    conn = createConnection();
                }
            } catch (Exception e) {
                // can't tell if it's valid, create a new one
                System.out.println("WARN: Connection validation failed: " + e.getMessage());
                conn = createConnection();
            }
        } else {
            // no available connections
            if (totalCreated < maxSize) {
                conn = createConnection();
            } else {
                // we're at max but Bob says "just make another one"
                // this is why the DBA sees 200 sessions sometimes
                System.out.println("WARN: Connection pool exhausted (max=" + maxSize + ", created=" + totalCreated + "). Creating connection anyway.");
                conn = createConnection();
            }
        }

        usedConnections.add(conn);
        return conn;
    }

    /**
     * Return a connection to the pool.
     * 
     * If you pass in a connection that didn't come from this pool,
     * it gets added anyway. "It's a feature, not a bug." - Bob
     */
    public synchronized void releaseConnection(Connection conn) {
        if (conn == null) return;

        usedConnections.remove(conn);

        try {
            if (!conn.isClosed()) {
                // put it back in the available pool
                // NOTE: we don't reset auto-commit, transaction isolation, 
                // or any other connection state. If the previous user changed 
                // something, the next user gets that state. Fun!
                availableConnections.add(conn);
            } else {
                // already closed, just discard it
                System.out.println("WARN: Released connection was already closed.");
            }
        } catch (Exception e) {
            // can't tell, just discard it
            System.out.println("WARN: Error checking released connection: " + e.getMessage());
        }
    }

    /**
     * How many connections are sitting in the pool.
     */
    public synchronized int getAvailableCount() {
        return availableConnections.size();
    }

    /**
     * How many connections are checked out.
     */
    public synchronized int getUsedCount() {
        return usedConnections.size();
    }

    /**
     * Initialize the pool by reading db.properties (same file ConnectionHelper uses)
     * and pre-creating the initial batch of connections.
     * 
     * If properties aren't found, falls back to HSQLDB like ConnectionHelper does.
     */
    private void initializePool() {
        try {
            Properties props = new Properties();
            InputStream is = ConnectionPool.class.getClassLoader()
                    .getResourceAsStream("db.properties");

            if (is != null) {
                props.load(is);
                is.close();
                driver = props.getProperty("db.driver", "org.hsqldb.jdbc.JDBCDriver");
                url = props.getProperty("db.url", "jdbc:hsqldb:mem:bigcorpdb");
                username = props.getProperty("db.username", "sa");
                password = props.getProperty("db.password", "");

                // pool size overrides (added in 2002 after Bob's "5 is enough" proved wrong)
                String initSizeStr = props.getProperty("pool.initialSize");
                if (initSizeStr != null) {
                    try {
                        initialSize = Integer.parseInt(initSizeStr.trim());
                    } catch (NumberFormatException nfe) {
                        System.err.println("WARN: Invalid pool.initialSize value: " + initSizeStr);
                    }
                }
                String maxSizeStr = props.getProperty("pool.maxSize");
                if (maxSizeStr != null) {
                    try {
                        maxSize = Integer.parseInt(maxSizeStr.trim());
                    } catch (NumberFormatException nfe) {
                        System.err.println("WARN: Invalid pool.maxSize value: " + maxSizeStr);
                    }
                }
            } else {
                System.out.println("WARN: db.properties not found, using HSQLDB defaults for pool");
                driver = "org.hsqldb.jdbc.JDBCDriver";
                url = "jdbc:hsqldb:mem:bigcorpdb";
                username = "sa";
                password = "";
            }

            Class.forName(driver);

            // pre-create the initial connections
            System.out.println("ConnectionPool: creating " + initialSize + " initial connections to " + url);
            for (int i = 0; i < initialSize; i++) {
                Connection conn = createConnection();
                availableConnections.add(conn);
            }

            initialized = true;
            System.out.println("ConnectionPool initialized: " + initialSize + " connections ready (max " + maxSize + ")");

        } catch (Exception e) {
            System.err.println("ERROR: Failed to initialize connection pool: " + e.getMessage());
            e.printStackTrace();
            // try HSQLDB fallback like ConnectionHelper does
            driver = "org.hsqldb.jdbc.JDBCDriver";
            url = "jdbc:hsqldb:mem:bigcorpdb";
            username = "sa";
            password = "";
            try {
                Class.forName(driver);
                for (int i = 0; i < initialSize; i++) {
                    Connection conn = createConnection();
                    availableConnections.add(conn);
                }
                initialized = true;
                System.out.println("ConnectionPool: fell back to HSQLDB in-memory database");
            } catch (Exception ex) {
                throw new RuntimeException("Cannot initialize connection pool even with HSQLDB fallback", ex);
            }
        }
    }

    /**
     * Create a single database connection.
     * On Oracle, sets NLS_DATE_FORMAT per-connection (same hack as ConnectionHelper).
     */
    private Connection createConnection() {
        try {
            Connection conn = DriverManager.getConnection(url, username, password);
            totalCreated++;

            // HACK: same NLS_DATE_FORMAT fix as ConnectionHelper
            // copied from there because "it needs to be in both places" - Dave
            // TODO: refactor into shared method (JIRA-2301)
            if (driver != null && driver.contains("oracle")) {
                Statement stmt = conn.createStatement();
                stmt.execute("ALTER SESSION SET NLS_DATE_FORMAT = 'YYYY-MM-DD HH24:MI:SS'");
                stmt.execute("ALTER SESSION SET NLS_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF'");
                stmt.close();
            }

            return conn;
        } catch (Exception e) {
            System.err.println("ERROR: Failed to create database connection: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Connection creation failed", e);
        }
    }

    /**
     * Destroy the pool. Closes all connections.
     * Called at shutdown (if we remember to call it, which we usually don't).
     * 
     * JIRA-2178: "JVM shutdown hook for pool cleanup"
     * Status: Open, Priority: Low, Assigned: Nobody
     */
    public synchronized void shutdown() {
        System.out.println("ConnectionPool: shutting down. Closing " + 
            (availableConnections.size() + usedConnections.size()) + " connections...");

        // close available connections
        for (int i = 0; i < availableConnections.size(); i++) {
            try {
                ((Connection) availableConnections.get(i)).close();
            } catch (Exception e) {
                // ignore - we're shutting down anyway
            }
        }
        availableConnections.clear();

        // close used connections (these are "leaked" but we're shutting down so who cares)
        for (int i = 0; i < usedConnections.size(); i++) {
            try {
                ((Connection) usedConnections.get(i)).close();
            } catch (Exception e) {
                // ignore
            }
        }
        usedConnections.clear();

        totalCreated = 0;
        initialized = false;
        instance = null;
        System.out.println("ConnectionPool: shutdown complete.");
    }
}
