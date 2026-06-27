package com.bigcorp.orderengine.consumer;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.db.DatabaseBootstrap;
import com.bigcorp.common.mq.MessageQueueHelper;

/**
 * Main entry point for the BigCorp Order Engine.
 * 
 * Initializes database and message queue connections, then starts
 * the order message listener.
 * 
 * Run this class to start consuming trade orders from the queue.
 * Use Ctrl-C to stop (shutdown hook will clean up).
 * 
 * NOTE: Make sure the pricing-service is running first, or all
 * price lookups will fall back to the database cache.
 * 
 * @author Bob
 * @since 1.0
 */
public class EngineMain {

    // version bumped from 1.2 to 1.3 for the commission fix
    // (the fix was never actually made - JIRA-2501)
    private static final String VERSION = "1.3";

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  BigCorp Order Engine v" + VERSION + " starting...");
        System.out.println("==============================================");
        System.out.println("");

        // initialize database
        System.out.println("Initializing database connection...");
        try {
            ConnectionHelper.init();
            DatabaseBootstrap.bootstrap();
            System.out.println("Database ready.");
        } catch (Exception e) {
            System.err.println("FATAL: Database initialization failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // initialize message queue
        System.out.println("Initializing message queue...");
        try {
            MessageQueueHelper.init();
            System.out.println("Message queue ready.");
        } catch (Exception e) {
            System.err.println("FATAL: MQ initialization failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // create and start listener
        final OrderMessageListener listener = new OrderMessageListener();

        // add shutdown hook for graceful stop
        // (learned about this after the "kill -9 corruption incident" of 2001)
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("");
                System.out.println("Shutdown signal received, stopping engine...");
                listener.stop();
                // give it a moment to finish current processing
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // ignore
                }
                System.out.println("Order Engine shut down.");
            }
        });

        System.out.println("");
        System.out.println("Order Engine is ready. Waiting for orders...");
        System.out.println("(Press Ctrl-C to stop)");
        System.out.println("");

        // this blocks until stop() is called
        listener.startListening();
    }
}
