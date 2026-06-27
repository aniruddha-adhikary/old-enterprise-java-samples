package com.bigcorp.common.db;

import java.io.InputStream;
import java.util.Properties;

/**
 * Abstract DAO Factory - Sun J2EE Blueprint pattern.
 *
 * Returns the correct DAO implementation based on the configured database.
 * Currently supports Oracle and HSQLDB. Was supposed to also support DB2
 * but the DB2 migration was cancelled after the POC failed.
 * ("DB2 doesn't support our SQL syntax" was the official reason.
 *  The real reason was nobody could get the DB2 driver to work.)
 *
 * Usage:
 *   DAOFactory factory = DAOFactory.getFactory();
 *   String daoClass = factory.getOrderDAOClassName();
 *   // then use Class.forName(daoClass).newInstance()
 *
 * "The factory pattern lets us swap databases without changing business logic"
 * - from the design document nobody reads anymore
 *
 * In practice, the Oracle and HSQLDB DAOs have slightly different SQL
 * (sequences vs IDENTITY, SYSDATE vs CURRENT_TIMESTAMP, etc.) so swapping
 * databases is not as seamless as the design doc promised.
 *
 * JIRA-1923: DAO Factory implementation
 * JIRA-2045: Add DB2 support (CANCELLED)
 *
 * @author Dave
 * @since 1.2
 */
public abstract class DAOFactory {

    // Database type constants
    // Use int instead of enum because "enums aren't in Java 1.3" - Dave
    // (We're on 1.4 now but nobody changed the code)
    public static final int ORACLE = 1;
    public static final int HSQLDB = 2;
    // public static final int DB2 = 3; // cancelled per JIRA-2045

    /**
     * Get the default factory based on what database we're configured for.
     * Reads db.properties to figure out which database we're using.
     * 
     * "Auto-detection is better than configuration" - Dave
     * "Auto-detection is why we have bugs" - Bob
     */
    public static DAOFactory getFactory() {
        int dbType = detectDatabaseType();
        return getFactory(dbType);
    }

    /**
     * Get a factory for a specific database type.
     * Pass in ORACLE or HSQLDB constant.
     * 
     * If you pass in something we don't recognize, you get HSQLDB
     * because "that's the safe default" - Dave
     */
    public static DAOFactory getFactory(int dbType) {
        switch (dbType) {
            case ORACLE:
                return new OracleDAOFactory();
            case HSQLDB:
                return new HsqldbDAOFactory();
            // case DB2:
            //     return new Db2DAOFactory(); // never written
            default:
                System.out.println("WARN: Unknown database type " + dbType + ", defaulting to HSQLDB factory");
                return new HsqldbDAOFactory();
        }
    }

    /**
     * Get the class name for the Order DAO implementation.
     * Returns a String because we don't want common-lib to depend on order-engine.
     * (In the real J2EE version these would return DAO interfaces defined in common-lib,
     * but we didn't have time to extract the interfaces. JIRA-2198.)
     * 
     * The caller does Class.forName(className).newInstance() which is
     * "perfectly safe" according to Dave. The ClassNotFoundException 
     * that happens when the module isn't on the classpath is "a deployment issue."
     */
    public abstract String getOrderDAOClassName();

    /**
     * Get the class name for the Settlement DAO implementation.
     * Same pattern as getOrderDAOClassName().
     */
    public abstract String getSettlementDAOClassName();

    /**
     * Detect what database we're using from the JDBC URL in db.properties.
     * 
     * This is the same properties file ConnectionHelper reads.
     * We read it again here because "we can't depend on ConnectionHelper being
     * initialized first" - Dave. Yes, we read the same file twice. No, it's
     * not a performance problem because "it only happens once at startup."
     */
    public static int detectDatabaseType() {
        try {
            Properties props = new Properties();
            InputStream is = DAOFactory.class.getClassLoader()
                    .getResourceAsStream("db.properties");

            if (is != null) {
                props.load(is);
                is.close();

                String jdbcUrl = props.getProperty("db.url", "");

                if (jdbcUrl.contains("oracle")) {
                    System.out.println("DAOFactory: detected Oracle database from JDBC URL");
                    return ORACLE;
                } else if (jdbcUrl.contains("hsqldb")) {
                    System.out.println("DAOFactory: detected HSQLDB database from JDBC URL");
                    return HSQLDB;
                }
                // else if (jdbcUrl.contains("db2")) {
                //     return DB2; // would have been nice
                // }
                else {
                    System.out.println("WARN: DAOFactory: unrecognized JDBC URL '" + jdbcUrl + "', defaulting to HSQLDB");
                    return HSQLDB;
                }
            } else {
                // no properties file = dev mode = HSQLDB
                System.out.println("DAOFactory: no db.properties found, defaulting to HSQLDB");
                return HSQLDB;
            }

        } catch (Exception e) {
            System.err.println("ERROR: DAOFactory: failed to detect database type: " + e.getMessage());
            e.printStackTrace();
            // default to HSQLDB because "it always works" - Dave
            return HSQLDB;
        }
    }
}
