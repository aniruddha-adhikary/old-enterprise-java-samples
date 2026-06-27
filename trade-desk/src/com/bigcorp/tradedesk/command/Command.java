package com.bigcorp.tradedesk.command;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Command interface for the Front Controller pattern.
 * Each action maps to a Command implementation.
 *
 * Based on the Command pattern from "Design Patterns" (GoF) and
 * adapted for web use per "Core J2EE Patterns" (Alur, Crupi, Malks).
 *
 * @author Bob
 * @since 2.0
 */
public interface Command {

    /**
     * Execute the command and return an HTML response body.
     * The caller (FrontControllerServlet) will wrap this in the
     * standard BigCorp page template.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @return HTML fragment to embed in the page body
     * @throws Exception if anything goes wrong (caller handles it)
     */
    String execute(HttpServletRequest request, HttpServletResponse response) throws Exception;
}
