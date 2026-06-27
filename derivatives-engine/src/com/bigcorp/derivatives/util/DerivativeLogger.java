package com.bigcorp.derivatives.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Simple logging utility for the derivatives module.
 * 
 * The rest of the codebase just does System.out.println() everywhere
 * which makes it impossible to tell which module produced which log line.
 * We prefix everything with [DERIV] and a timestamp so our logs are
 * easy to grep in production.
 * 
 * @author External contractor
 * @since 2004-Q3
 */
public class DerivativeLogger {

    private static final String PREFIX = "[DERIV]";
    private static final String DATE_FMT = "yyyy-MM-dd HH:mm:ss.SSS";

    private final String className;

    public DerivativeLogger(Class clazz) {
        this.className = clazz != null ? clazz.getName() : "unknown";
    }

    public void info(String msg) {
        System.out.println(formatLine("INFO", msg));
    }

    public void warn(String msg) {
        System.out.println(formatLine("WARN", msg));
    }

    public void error(String msg) {
        System.err.println(formatLine("ERROR", msg));
    }

    public void error(String msg, Throwable t) {
        System.err.println(formatLine("ERROR", msg));
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }

    public void debug(String msg) {
        System.out.println(formatLine("DEBUG", msg));
    }

    private String formatLine(String level, String msg) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FMT);
        StringBuffer sb = new StringBuffer();
        sb.append(sdf.format(new Date()));
        sb.append(" ").append(PREFIX);
        sb.append(" [").append(level).append("]");
        sb.append(" ").append(className);
        sb.append(" - ").append(msg);
        return sb.toString();
    }
}
