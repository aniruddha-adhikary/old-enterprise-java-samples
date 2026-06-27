package com.bigcorp.common.db;

/**
 * HSQLDB-specific DAO Factory.
 * 
 * Returns the HSQLDB DAO implementations for development and testing.
 * HSQLDB runs in-memory so it's fast but doesn't persist data across restarts.
 * (Bob asked "why do my test orders disappear?" approximately once a month.)
 * 
 * The HSQLDB DAOs use standard SQL where possible, with HSQLDB-specific
 * syntax for things Oracle does differently (IDENTITY columns instead of
 * sequences, CURRENT_TIMESTAMP instead of SYSDATE, etc.).
 *
 * Currently the HSQLDB DAOs are the same classes as the "default" DAOs
 * because HSQLDB was the first database we supported. The Oracle DAOs
 * were added later when "the client insisted on Oracle." 
 *
 * @author Dave
 * @since 1.2
 */
public class HsqldbDAOFactory extends DAOFactory {

    /**
     * HSQLDB Order DAO - the "standard" implementation.
     * Uses the same OrderDAO class that's been there since day one.
     * The one Bob wrote on that Friday afternoon when the deadline was Monday.
     */
    public String getOrderDAOClassName() {
        // This is the original OrderDAO in order-engine
        // It uses IDENTITY columns and standard SQL
        return "com.bigcorp.orderengine.dao.OrderDAO";
    }

    /**
     * HSQLDB Settlement DAO.
     * Uses the standard SettlementDAO that doesn't need Oracle stored procs.
     */
    public String getSettlementDAOClassName() {
        // This is the original SettlementDAO in settlement-gateway
        return "com.bigcorp.settlement.dao.SettlementDAO";
    }
}
