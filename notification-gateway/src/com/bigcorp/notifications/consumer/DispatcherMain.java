package com.bigcorp.notifications.consumer;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.db.DatabaseBootstrap;
import com.bigcorp.common.mq.MessageQueueHelper;

/**
 * Main entry point for the Notification Gateway service.
 * 
 * This is a standalone Java process that connects to the message queue
 * and dispatches email/SMS notifications. Run it alongside the
 * trade-desk and order-engine.
 * 
 * Startup sequence:
 *   1. Initialize database connection
 *   2. Bootstrap database tables (if not exist)
 *   3. Initialize message queue connection
 *   4. Start notification listener
 * 
 * To stop: Ctrl+C (the shutdown hook will handle cleanup)
 * 
 * @author Karen
 * @since 1.1
 */
public class DispatcherMain {

    private static NotificationListener listener;

    public static void main(String[] args) {
        System.out.println("================================================");
        System.out.println("  BigCorp Notification Gateway v1.1");
        System.out.println("  Email & SMS Dispatch Service");
        System.out.println("================================================");
        System.out.println("");

        // Register shutdown hook first so we can always clean up
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Shutdown signal received...");
                if (listener != null) {
                    listener.stop();
                }
                System.out.println("Notification Gateway shutdown complete.");
            }
        });

        try {
            // Step 1: Database
            System.out.println("Initializing database connection...");
            ConnectionHelper.init();

            // Step 2: Bootstrap tables
            System.out.println("Bootstrapping database tables...");
            DatabaseBootstrap.bootstrap();

            // Step 3: Message Queue
            System.out.println("Initializing message queue connection...");
            MessageQueueHelper.init();

            // Step 4: Start listening
            System.out.println("Starting notification listener...");
            System.out.println("");
            listener = new NotificationListener();
            listener.startListening();

        } catch (Exception e) {
            System.err.println("FATAL: Notification Gateway failed to start: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
