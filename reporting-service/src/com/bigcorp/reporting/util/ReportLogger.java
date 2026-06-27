package com.bigcorp.reporting.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logging utility for the reporting module.
 * 
 * We don't use whatever the main codebase uses for logging because
 * (a) I couldn't find it in the docs and (b) we want our own
 * log format with the module prefix.
 * 
 * @author contractor (reporting team)
 * @since 2013-Q2
 */
public class ReportLogger {

    private String className;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ReportLogger(Class clazz) {
        this.className = clazz.getName();
    }

    public void info(String msg) {
        System.out.println(FMT.format(new Date()) + " [RPT-INFO] " + className + " - " + msg);
    }

    public void warn(String msg) {
        System.out.println(FMT.format(new Date()) + " [RPT-WARN] " + className + " - " + msg);
    }

    public void error(String msg) {
        System.err.println(FMT.format(new Date()) + " [RPT-ERROR] " + className + " - " + msg);
    }

    public void error(String msg, Throwable t) {
        System.err.println(FMT.format(new Date()) + " [RPT-ERROR] " + className + " - " + msg);
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }

    public void debug(String msg) {
        // debug is off by default, flip to true if you need it
        if ("true".equals(System.getProperty("reporting.debug"))) {
            System.out.println(FMT.format(new Date()) + " [RPT-DEBUG] " + className + " - " + msg);
        }
    }
}
