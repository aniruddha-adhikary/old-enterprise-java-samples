package com.bigcorp.common.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Audit log event domain object.
 * 
 * Maps to the AUDIT_LOG table. Each event records a significant
 * action in the order lifecycle (filled, rejected, settled, billed).
 * 
 * @author Audit team
 * @since 2.1
 */
public class AuditEvent implements Serializable {

    private static final long serialVersionUID = 105L;

    public static final String EVENT_ORDER_FILLED = "ORDER_FILLED";
    public static final String EVENT_ORDER_REJECTED = "ORDER_REJECTED";
    public static final String EVENT_ORDER_SETTLED = "ORDER_SETTLED";
    public static final String EVENT_BILLING_CHARGED = "BILLING_CHARGED";

    public static final String SOURCE_ORDER_ENGINE = "order-engine";
    public static final String SOURCE_SETTLEMENT = "settlement-gateway";
    public static final String SOURCE_AUDIT_SERVICE = "audit-service";

    public static final String ENTITY_ORDER = "ORDER";
    public static final String ENTITY_BILLING = "BILLING";

    private int logId;
    private String eventType;
    private String sourceSystem;
    private String entityType;
    private String entityId;
    private String description;
    private Date logDate;
    private String userId;

    public AuditEvent() {
        this.logDate = new Date();
    }

    public int getLogId() {
        return logId;
    }

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getLogDate() {
        return logDate;
    }

    public void setLogDate(Date logDate) {
        this.logDate = logDate;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String toString() {
        return "AuditEvent[" + eventType + " " + entityType + ":" + entityId + " by " + userId + "]";
    }
}
