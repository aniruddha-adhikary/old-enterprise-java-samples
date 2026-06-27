package com.bigcorp.notifications.consumer;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.model.Notification;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.common.xml.XmlHelper;
import com.bigcorp.notifications.email.EmailDispatcher;
import com.bigcorp.notifications.sms.SMSDispatcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Date;

/**
 * Listens on the NOTIFICATIONS queue and dispatches messages
 * to the appropriate channel (Email, SMS, or FAX).
 * 
 * The FAX channel was deprecated in 2002 but we still have to
 * handle it because some legacy batch jobs still put FAX messages
 * on the queue. We log a warning and move on.
 * 
 * Threading: this class runs in a single thread. We tried making it
 * multi-threaded once but the SMTP relay couldn't handle it and
 * started dropping connections. Single thread is "fast enough."
 * 
 * @author Karen
 * @since 1.1
 */
public class NotificationListener {

    private boolean running = false;

    // poll timeout - 5 seconds seems to be the sweet spot
    // shorter = too much CPU, longer = too slow to shut down
    private static final long POLL_TIMEOUT_MS = 5000;
    private static final int MAX_RETRY_COUNT = 3;

    private EmailDispatcher emailDispatcher;
    private SMSDispatcher smsDispatcher;

    public NotificationListener() {
        this.emailDispatcher = new EmailDispatcher();
        this.smsDispatcher = new SMSDispatcher();
    }

    /**
     * Start listening for notification messages.
     * This method blocks until stop() is called from another thread.
     */
    public void startListening() {
        running = true;
        System.out.println("NotificationListener started, polling " + MessageQueueHelper.QUEUE_NOTIFICATIONS);

        while (running) {
            try {
                String messageXml = MessageQueueHelper.receiveMessage(
                        MessageQueueHelper.QUEUE_NOTIFICATIONS, POLL_TIMEOUT_MS);

                if (messageXml == null) {
                    // no message within timeout, loop again
                    continue;
                }

                System.out.println("Received notification message (" + messageXml.length() + " chars)");

                Notification notif = XmlHelper.unmarshalNotification(messageXml);
                if (notif == null) {
                    System.err.println("ERROR: Failed to unmarshal notification, message discarded");
                    continue;
                }

                processNotification(notif);

            } catch (Exception e) {
                // Don't let any single exception kill the listener
                // This has saved us multiple times in production
                System.err.println("ERROR: Exception in notification listener loop: " + e.getMessage());
                e.printStackTrace();
                try {
                    Thread.sleep(1000); // back off a bit on errors
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.out.println("NotificationListener stopped.");
    }

    /**
     * Process a single notification - route to the appropriate dispatcher
     * and persist to database.
     */
    private void processNotification(Notification notif) {
        boolean dispatchSuccess = false;

        // Route to appropriate channel
        // This used to be a simple if/else but then the sales team wanted
        // different behavior for different notification types on different
        // channels, so it grew into this mess. TODO: refactor (added 2001-03-15)
        if (Notification.CHANNEL_EMAIL.equals(notif.getChannel())) {
            // Email dispatch
            try {
                emailDispatcher.sendEmail(notif);
                dispatchSuccess = true;
            } catch (Exception e) {
                System.err.println("ERROR: Email dispatch failed for " + notif.getNotificationId() 
                        + ": " + e.getMessage());
                dispatchSuccess = false;
            }
        } else if (Notification.CHANNEL_SMS.equals(notif.getChannel())) {
            // SMS dispatch
            try {
                smsDispatcher.sendSMS(notif);
                dispatchSuccess = true;
            } catch (Exception e) {
                System.err.println("ERROR: SMS dispatch failed for " + notif.getNotificationId() 
                        + ": " + e.getMessage());
                dispatchSuccess = false;
            }
        } else if (Notification.CHANNEL_FAX.equals(notif.getChannel())) {
            // FAX is no longer supported but we still get these from legacy systems
            System.out.println("WARN: FAX channel not supported, notification " 
                    + notif.getNotificationId() + " will not be delivered");
            // mark as failed since we can't actually send it
            dispatchSuccess = false;
        } else {
            // Unknown channel - this shouldn't happen but it did once
            // when someone put "PAGER" in the database
            System.err.println("ERROR: Unknown notification channel: " + notif.getChannel()
                    + " for notification " + notif.getNotificationId());
            dispatchSuccess = false;
        }

        // Handle success/failure
        if (dispatchSuccess) {
            notif.setStatus(Notification.STATUS_SENT);
            notif.setSentDate(new Date());
        } else {
            // Retry logic - re-queue if under max retries
            int currentRetry = notif.getRetryCount();
            if (currentRetry < MAX_RETRY_COUNT) {
                notif.setRetryCount(currentRetry + 1);
                System.out.println("Re-queuing notification " + notif.getNotificationId() 
                        + " (retry " + notif.getRetryCount() + "/" + MAX_RETRY_COUNT + ")");
                try {
                    String requeueXml = XmlHelper.marshalNotification(notif);
                    if (requeueXml != null) {
                        MessageQueueHelper.sendMessage(MessageQueueHelper.QUEUE_NOTIFICATIONS, requeueXml);
                    }
                } catch (Exception e) {
                    System.err.println("ERROR: Failed to re-queue notification: " + e.getMessage());
                }
                return; // don't save to DB yet, will be processed again
            } else {
                notif.setStatus(Notification.STATUS_FAILED);
                System.err.println("WARN: Notification " + notif.getNotificationId() 
                        + " exceeded max retries, marking as FAILED");
            }
        }

        // Persist to database
        saveNotification(notif);
    }

    /**
     * Save notification record to the NOTIFICATIONS table.
     * This is a simple INSERT - we don't update existing records because
     * "it's easier to just insert a new row" (this was a bad decision).
     */
    private void saveNotification(Notification notif) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = ConnectionHelper.getConnection();

            String sql = "INSERT INTO NOTIFICATIONS (NOTIFICATION_ID, NOTIFICATION_TYPE, RECIPIENT, " 
                    + "SUBJECT, BODY, CHANNEL, STATUS, ORDER_ID, CREATED_DATE, SENT_DATE, RETRY_COUNT) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            ps = conn.prepareStatement(sql);
            ps.setString(1, notif.getNotificationId());
            ps.setString(2, notif.getType());
            ps.setString(3, notif.getRecipient());
            ps.setString(4, notif.getSubject());
            ps.setString(5, notif.getBody());
            ps.setString(6, notif.getChannel());
            ps.setString(7, notif.getStatus());
            ps.setString(8, notif.getOrderId());

            if (notif.getCreatedDate() != null) {
                ps.setTimestamp(9, new Timestamp(notif.getCreatedDate().getTime()));
            } else {
                ps.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
            }

            if (notif.getSentDate() != null) {
                ps.setTimestamp(10, new Timestamp(notif.getSentDate().getTime()));
            } else {
                ps.setNull(10, java.sql.Types.TIMESTAMP);
            }

            ps.setInt(11, notif.getRetryCount());

            ps.executeUpdate();
            System.out.println("Saved notification " + notif.getNotificationId() + " to database");

        } catch (Exception e) {
            // swallow the exception - we don't want to lose the message
            // just because the DB write failed. The notification was already sent.
            System.err.println("ERROR: Failed to save notification to DB: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Stop the listener gracefully.
     * The loop will exit after the current poll timeout completes.
     */
    public void stop() {
        running = false;
    }
}
