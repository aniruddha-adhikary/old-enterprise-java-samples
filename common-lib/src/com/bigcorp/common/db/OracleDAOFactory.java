package com.bigcorp.common.db;

/**
 * Oracle-specific DAO Factory.
 * 
 * Returns the Oracle DAO implementations which use Oracle-specific SQL
 * (sequences, SYSDATE, NVL, ROWNUM, etc.).
 * 
 * In theory, the Oracle DAOs and HSQLDB DAOs implement the same interface
 * and are interchangeable. In practice, the Oracle DAOs have several
 * Oracle-specific hacks that Bob added "because it's faster."
 * 
 * NOTE: The DAO class names returned here must match the actual classes
 * in order-engine and settlement-gateway. If someone renames a class
 * without updating this factory, you get ClassNotFoundException at runtime.
 * "That's a deployment issue, not a code issue." - Dave
 *
 * @author Dave
 * @since 1.2
 */
public class OracleDAOFactory extends DAOFactory {

    /**
     * Oracle version of the Order DAO.
     * Uses Oracle sequences for ID generation and SYSDATE for timestamps.
     * Also uses that weird CONNECT BY trick for hierarchical queries
     * that nobody understands except Bob.
     */
    public String getOrderDAOClassName() {
        // This class lives in order-engine module
        // If order-engine isn't on the classpath, Class.forName() will fail
        // but "that's the caller's problem" - Dave
        return "com.bigcorp.orderengine.dao.OracleOrderDAO";
    }

    /**
     * Oracle version of the Settlement DAO.
     * Uses Oracle sequences and has that stored procedure call
     * for the end-of-day batch that the DBA wrote.
     */
    public String getSettlementDAOClassName() {
        // This class lives in settlement-gateway module
        return "com.bigcorp.settlement.dao.OracleSettlementDAO";
    }
}
