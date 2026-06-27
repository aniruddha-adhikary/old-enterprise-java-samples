package com.bigcorp.tradedesk.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Intercepting Filter - logs every request with timestamp, URL,
 * client IP, and response time.
 *
 * Added after the "who keeps crashing the system" incident of 2001.
 * Turned out it was the Henderson account running a script that
 * submitted 500 orders per minute. This filter helped us figure that out.
 *
 * Outputs to System.out because "we'll add Log4j later."
 * (it's been two years)
 *
 * @author Dave
 * @since 2.0
 */
public class RequestLoggingFilter implements Filter {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("[RequestLoggingFilter] Initialized.");
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        long startTime = System.currentTimeMillis();

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String url = httpRequest.getRequestURI();
        String queryString = httpRequest.getQueryString();
        String clientIP = request.getRemoteAddr();
        String method = httpRequest.getMethod();

        // build the full URL for logging
        String fullUrl = url;
        if (queryString != null && queryString.length() > 0) {
            fullUrl = url + "?" + queryString;
        }

        // log request start
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        String timestamp = sdf.format(new Date());
        System.out.println("[REQUEST] " + timestamp + " " + method + " " + fullUrl + " from " + clientIP);

        // pass to next filter/servlet
        chain.doFilter(request, response);

        // log response time
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("[REQUEST] " + url + " completed in " + elapsed + "ms");
    }

    public void destroy() {
        System.out.println("[RequestLoggingFilter] Destroyed.");
    }
}
