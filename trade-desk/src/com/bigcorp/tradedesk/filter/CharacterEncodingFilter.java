package com.bigcorp.tradedesk.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * Sets character encoding to ISO-8859-1 on all requests.
 * (UTF-8 would be more modern but "the database can't handle it")
 * Added after the Henderson account sent orders with special chars
 * and broke everything. Their trader has an umlaut in his name
 * and it corrupted the XML message on the MQ queue.
 *
 * We could probably switch to UTF-8 now but nobody wants to test
 * all the downstream systems. The settlement gateway parses the
 * XML with a SAX parser that might choke on multi-byte chars.
 * (JIRA-2456, wontfix)
 *
 * @author Karen
 * @since 2.0
 */
public class CharacterEncodingFilter implements Filter {

    private String encoding = "ISO-8859-1";

    public void init(FilterConfig filterConfig) throws ServletException {
        // allow override via init-param, but default to ISO-8859-1
        String configuredEncoding = filterConfig.getInitParameter("encoding");
        if (configuredEncoding != null && configuredEncoding.trim().length() > 0) {
            encoding = configuredEncoding.trim();
        }
        System.out.println("[CharacterEncodingFilter] Initialized with encoding: " + encoding);
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        request.setCharacterEncoding(encoding);
        response.setCharacterEncoding(encoding);

        chain.doFilter(request, response);
    }

    public void destroy() {
        System.out.println("[CharacterEncodingFilter] Destroyed.");
    }
}
