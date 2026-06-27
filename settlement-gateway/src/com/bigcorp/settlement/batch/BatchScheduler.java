package com.bigcorp.settlement.batch;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.db.DatabaseBootstrap;
import com.bigcorp.common.mq.MessageQueueHelper;

import java.io.InputStream;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Scheduler for settlement batch processing.
 * 
 * Uses java.util.Timer for scheduling. In production this runs at 6 PM EST
 * via cron (the Unix admin set that up), but this Timer thing is just for 
 * testing and the standalone demo. 
 * 
 * Don't use this in production — the cron job calls BatchProcessor directly
 * via a shell script that also handles logging and alerting.
 * 
 * @author Dave (settlements team)
 * @since 1.1
 */
public class BatchScheduler {

    private Timer timer;
    private BatchProcessor processor;
    private long intervalMs;
    private boolean running;

    // default interval: 60 seconds (for demo; production uses cron at 6 PM)
    private static final long DEFAULT_INTERVAL_MS = 60000;

    public BatchScheduler() {
        this.processor = new BatchProcessor();
        this.running = false;
        loadConfig();
    }

    private void loadConfig() {
        try {
            Properties props = new Properties();
            InputStream is = getClass().getClassLoader().getResourceAsStream("settlement.properties");
            if (is != null) {
                props.load(is);
                is.close();
                String intervalStr = props.getProperty("batch.interval.ms", String.valueOf(DEFAULT_INTERVAL_MS));
                intervalMs = Long.parseLong(intervalStr);
            } else {
                intervalMs = DEFAULT_INTERVAL_MS;
            }
        } catch (Exception e) {
            intervalMs = DEFAULT_INTERVAL_MS;
            System.err.println("WARN: Could not load batch config, using default interval: " + intervalMs + "ms");
        }
    }

    /**
     * Start the batch scheduler.
     * Schedules the batch processor to run at a fixed interval.
     */
    public void start() {
        if (running) {
            System.out.println("WARN: BatchScheduler is already running");
            return;
        }

        timer = new Timer("SettlementBatchTimer", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                try {
                    processor.processBatch();
                } catch (Exception e) {
                    // catch everything so the timer doesn't die
                    System.err.println("ERROR: Batch processing threw exception: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 5000, intervalMs); // initial delay of 5 seconds, then fixed interval

        running = true;
        System.out.println("BatchScheduler started. Interval: " + intervalMs + "ms");
        System.out.println("(In production this runs at 6 PM via cron, this Timer thing is just for testing)");
    }

    /**
     * Stop the batch scheduler.
     */
    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        running = false;
        System.out.println("BatchScheduler stopped.");
    }

    /**
     * Standalone entry point for testing/demo.
     * Initializes DB and MQ, then runs the scheduler.
     */
    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("BigCorp Settlement Gateway - Batch Scheduler");
        System.out.println("====================================================");
        System.out.println();

        // Initialize infrastructure
        ConnectionHelper.init();
        DatabaseBootstrap.bootstrap();
        MessageQueueHelper.init();

        // Start the scheduler
        final BatchScheduler scheduler = new BatchScheduler();
        scheduler.start();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Shutting down settlement batch scheduler...");
                scheduler.stop();
            }
        });

        // Keep main thread alive
        System.out.println("Press Ctrl+C to stop.");
        try {
            while (true) {
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            scheduler.stop();
        }
    }
}
