package com.bigcorp.common.dao;

import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base DAO consolidating common JDBC boilerplate.
 * 
 * Wave 11 tech-debt paydown: all module DAOs duplicate the same
 * connection-get / prepare / execute / close-quietly pattern.
 * This base class extracts that into reusable template methods.
 * 
 * Subclasses implement {@link #mapRow(ResultSet)} to convert a row
 * into a domain object.
 * 
 * Migration status:
 * - OrderDAO: migrated (extends BaseDAO)
 * - AuditDAO: migrated (extends BaseDAO)
 * - SettlementDAO: NOT migrated — uses SFTP/file-writing patterns
 *   interleaved with DB access that don't fit the template method.
 * - ReportingDAO: NOT migrated — contractor module with its own
 *   connection management (reads from ReportConfig, not ConnectionHelper).
 *   Would require the contractor team to accept common-lib conventions.
 * 
 * @author architect
 * @since 2016-Q1
 */
public abstract class BaseDAO {

    /**
     * Map a single result-set row to a domain object.
     * Called by query methods for each row in the result set.
     */
    protected abstract Object mapRow(ResultSet rs) throws Exception;

    /**
     * Execute a query and return a list of mapped objects.
     */
    public List queryList(String sql) {
        List results = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        } catch (Exception e) {
            System.err.println("BaseDAO query error: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
        return results;
    }

    /**
     * Execute a parameterized query and return a list of mapped objects.
     */
    public List queryList(String sql, Object[] params) {
        List results = new ArrayList();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            ps = conn.prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
            }
            rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        } catch (Exception e) {
            System.err.println("BaseDAO parameterized query error: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
        return results;
    }

    /**
     * Execute a parameterized query and return a single object, or null.
     */
    public Object querySingle(String sql, Object[] params) {
        List results = queryList(sql, params);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Execute an update statement (INSERT, UPDATE, DELETE).
     * Returns the number of rows affected.
     */
    public int executeUpdate(String sql, Object[] params) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionHelper.getConnection();
            ps = conn.prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
            }
            return ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("BaseDAO update error: " + e.getMessage());
            return -1;
        } finally {
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Count rows matching a condition.
     */
    public int countRows(String tableName, String whereClause, Object[] params) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        if (whereClause != null && whereClause.length() > 0) {
            sql += " WHERE " + whereClause;
        }
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            ps = conn.prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
            }
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("BaseDAO count error: " + e.getMessage());
            return -1;
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }
}
