package com.bigcorp.tradedesk.command;

import java.util.Hashtable;

/**
 * Factory for Command objects. Maps action strings to Command instances.
 * Uses a Hashtable for lookups because "it's thread-safe."
 * (Bob read this in a Java book and has mentioned it in every code review since.)
 *
 * If the action is not found, returns an UnknownCommand that shows
 * an error page. Better than a NullPointerException, which is what
 * we had before (JIRA-2567).
 *
 * @author Bob (after reading "Core J2EE Patterns" by Deepak Alur)
 * @since 2.0
 */
public class CommandFactory {

    // Hashtable is thread-safe! (Bob keeps reminding us)
    private static Hashtable commands = new Hashtable();

    // static initializer block - commands are registered here
    // if you add a new command, add it here too
    // (yes, we know this should be configurable via XML, it's on the roadmap)
    static {
        commands.put("submitOrder", new SubmitOrderCommand());
        commands.put("viewStatus", new ViewOrderStatusCommand());
        commands.put("viewOrders", new ListOrdersCommand());
        commands.put("viewDashboard", new DashboardCommand());
        // alias - some bookmarks still use the old name
        commands.put("dashboard", new DashboardCommand());
    }

    /**
     * Get a Command for the given action name.
     * Returns UnknownCommand if the action is not recognized.
     *
     * @param action the action name (from request parameter or URL)
     * @return the Command to execute, never null
     */
    public static Command getCommand(String action) {
        if (action == null || action.trim().length() == 0) {
            // no action specified, show the dashboard by default
            // (management wanted this - JIRA-2810)
            return (Command) commands.get("viewDashboard");
        }

        Command cmd = (Command) commands.get(action.trim());
        if (cmd == null) {
            System.out.println("WARN: Unknown action requested: " + action);
            return new UnknownCommand();
        }
        return cmd;
    }
}
