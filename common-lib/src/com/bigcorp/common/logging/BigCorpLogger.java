package com.bigcorp.common.logging;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Unified logging facade for the BigCorp platform.
 * 
 * Wave 15 observability cleanup: the codebase has accumulated multiple
 * ad-hoc logging mechanisms:
 * - System.out.println throughout order-engine and common-lib
 * - System.err.println for errors (inconsistently)
 * - DerivativeLogger in derivatives-engine (own format, own prefix)
 * - ReportLogger in reporting-service (own format, own prefix)
 * - Direct System.out in settlement-gateway batch jobs
 * 
 * This facade provides a single, consistent logging API. New code
 * should use this. Existing code is migrated where feasible.
 * 
 * Migration status:
 * - common-lib/BaseDAO: migrated
 * - common-lib/RuleEngine: NOT migrated (too many callers depend on
 *   exact output format for log parsing scripts)
 * - order-engine: NOT migrated (risk of breaking MQ message listener
 *   error handling flow)
 * - derivatives-engine: NOT migrated (contractor module with own
 *   conventions, DerivativeLogger is tightly coupled)
 * - reporting-service: NOT migrated (contractor module, uses ReportLogger
 *   with own config)
 * - settlement-gateway: partially migrated (new RegulatoryExportJob uses it)
 * - trade-desk: NOT migrated (JSP/servlet layer uses stdout)
 * 
 * @author architect
 * @since 2023-Q1
 */
public class BigCorpLogger {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Log levels, ordered by severity.
     */
    public static final int LEVEL_DEBUG = 0;
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_WARN = 2;
    public static final int LEVEL_ERROR = 3;

    private static int globalLevel = LEVEL_INFO;

    private String moduleName;
    private String className;

    /**
     * Create a logger for a specific class.
     */
    public BigCorpLogger(Class clazz) {
        this.className = clazz.getName();
        this.moduleName = deriveModuleName(clazz);
    }

    /**
     * Create a logger with explicit module name.
     */
    public BigCorpLogger(String moduleName, Class clazz) {
        this.moduleName = moduleName;
        this.className = clazz.getName();
    }

    /**
     * Set the global log level.
     */
    public static void setGlobalLevel(int level) {
        globalLevel = level;
    }

    /**
     * Get the global log level.
     */
    public static int getGlobalLevel() {
        return globalLevel;
    }

    public void debug(String msg) {
        if (globalLevel <= LEVEL_DEBUG) {
            log("DEBUG", msg, null);
        }
    }

    public void info(String msg) {
        if (globalLevel <= LEVEL_INFO) {
            log("INFO", msg, null);
        }
    }

    public void warn(String msg) {
        if (globalLevel <= LEVEL_WARN) {
            log("WARN", msg, null);
        }
    }

    public void warn(String msg, Throwable t) {
        if (globalLevel <= LEVEL_WARN) {
            log("WARN", msg, t);
        }
    }

    public void error(String msg) {
        if (globalLevel <= LEVEL_ERROR) {
            log("ERROR", msg, null);
        }
    }

    public void error(String msg, Throwable t) {
        if (globalLevel <= LEVEL_ERROR) {
            log("ERROR", msg, t);
        }
    }

    /**
     * Core log method.
     * Format: timestamp [LEVEL] [module] className - message
     */
    private void log(String level, String msg, Throwable t) {
        String ts = FMT.format(new Date());
        String output = ts + " [" + level + "] [" + moduleName + "] " + className + " - " + msg;

        if ("ERROR".equals(level) || "WARN".equals(level)) {
            System.err.println(output);
            if (t != null) {
                t.printStackTrace(System.err);
            }
        } else {
            System.out.println(output);
            if (t != null) {
                t.printStackTrace(System.out);
            }
        }
    }

    /**
     * Derive module name from package.
     */
    private static String deriveModuleName(Class clazz) {
        String pkg = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
        if (pkg.startsWith("com.bigcorp.common")) return "common-lib";
        if (pkg.startsWith("com.bigcorp.orderengine")) return "order-engine";
        if (pkg.startsWith("com.bigcorp.tradedesk")) return "trade-desk";
        if (pkg.startsWith("com.bigcorp.pricing")) return "pricing-service";
        if (pkg.startsWith("com.bigcorp.settlement")) return "settlement-gateway";
        if (pkg.startsWith("com.bigcorp.audit")) return "audit-service";
        if (pkg.startsWith("com.bigcorp.derivatives")) return "derivatives-engine";
        if (pkg.startsWith("com.bigcorp.reporting")) return "reporting-service";
        if (pkg.startsWith("com.bigcorp.risk")) return "risk-engine";
        return "unknown";
    }
}
