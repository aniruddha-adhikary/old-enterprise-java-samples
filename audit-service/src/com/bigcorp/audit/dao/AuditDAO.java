package com.bigcorp.audit.dao;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.model.AuditEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

/**
 * Data access for AUDIT_LOG table.
 * 
 * Uses HSQLDB IDENTITY column for LOG_ID (auto-generated).
 * On Oracle the DBA's schema uses SEQ_AUDIT_LOG but IDENTITY
 * works on both, so we omit LOG_ID from the INSERT and let
 * the database assign it.
 * 
 * @author Audit team
 * @since 2.1
 */
public class AuditDAO {

    /**
     * Insert an audit event into the AUDIT_LOG table.
     */
    public void insertAuditEvent(AuditEvent event) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = ConnectionHelper.getConnection();

            String sql = "INSERT INTO AUDIT_LOG (EVENT_TYPE, SOURCE_SYSTEM, ENTITY_TYPE, "
                    + "ENTITY_ID, DESCRIPTION, LOG_DATE, USER_ID) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

            ps = conn.prepareStatement(sql);
            ps.setString(1, event.getEventType());
            ps.setString(2, event.getSourceSystem());
            ps.setString(3, event.getEntityType());
            ps.setString(4, event.getEntityId());
            ps.setString(5, event.getDescription());

            if (event.getLogDate() != null) {
                ps.setTimestamp(6, new Timestamp(event.getLogDate().getTime()));
            } else {
                ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            }

            ps.setString(7, event.getUserId());

            ps.executeUpdate();
            System.out.println("Audit event saved: " + event.getEventType() 
                    + " for " + event.getEntityId());

        } catch (Exception e) {
            System.err.println("ERROR: Failed to save audit event: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }
}
