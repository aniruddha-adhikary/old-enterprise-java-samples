package com.bigcorp.connector;

/**
 * Exception type for the Mainframe Connector resource adapter.
 * Modeled after javax.resource.ResourceException from the JCA spec.
 * 
 * Hand-rolled so we don't need the J2EE container on the classpath.
 * 
 * @author Vendor Integration Team (Apex Consulting)
 * @since connector-1.0
 */
public class ConnectorException extends Exception {

    private String errorCode;

    public ConnectorException(String message) {
        super(message);
    }

    public ConnectorException(String message, Throwable cause) {
        super(message);
        // Java 1.4 style — initCause instead of super(msg, cause)
        initCause(cause);
    }

    public ConnectorException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ConnectorException(String message, String errorCode, Throwable cause) {
        super(message);
        this.errorCode = errorCode;
        initCause(cause);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
