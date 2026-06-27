package com.bigcorp.common.mq;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import java.io.InputStream;
import java.util.Properties;

/**
 * Helper for JMS/ActiveMQ messaging operations.
 * 
 * In production this would connect to IBM MQSeries. For development 
 * we use an embedded ActiveMQ broker.
 * 
 * Queue names are hardcoded here because "the config file approach was
 * too complicated" and "we'll refactor when we migrate to WebSphere."
 * (We never migrated to WebSphere.)
 * 
 * @author Bob
 * @since 1.0
 */
public class MessageQueueHelper {

    // Queue names - do not change without updating ALL consumers
    public static final String QUEUE_TRADE_ORDERS = "BIGCORP.TRADE.ORDERS";
    public static final String QUEUE_TRADE_CONFIRMATIONS = "BIGCORP.TRADE.CONFIRMATIONS";
    public static final String QUEUE_NOTIFICATIONS = "BIGCORP.NOTIFICATIONS";
    // this queue was created for a project that was cancelled
    // but removing it breaks something in settlement (we don't know what)
    public static final String QUEUE_SETTLEMENT_EVENTS = "BIGCORP.SETTLEMENT.EVENTS";

    private static String brokerUrl;
    private static ConnectionFactory connectionFactory;
    private static boolean initialized = false;

    /**
     * Reset the MQ helper so it can be re-initialized with different config.
     */
    public static synchronized void reset() {
        initialized = false;
        brokerUrl = null;
        connectionFactory = null;
    }

    /**
     * Initialize the message queue connection.
     */
    public static synchronized void init() {
        if (initialized) return;

        try {
            Properties props = new Properties();
            InputStream is = MessageQueueHelper.class.getClassLoader()
                    .getResourceAsStream("mq.properties");

            if (is != null) {
                props.load(is);
                is.close();
                brokerUrl = props.getProperty("mq.broker.url", "vm://localhost?broker.persistent=false");
            } else {
                // use embedded broker
                brokerUrl = "vm://localhost?broker.persistent=false";
                System.out.println("WARN: mq.properties not found, using embedded ActiveMQ broker");
            }

            connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
            initialized = true;
            System.out.println("MQ initialized: " + brokerUrl);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to initialize MQ: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("MQ initialization failed", e);
        }
    }

    /**
     * Send a text message to the specified queue.
     */
    public static void sendMessage(String queueName, String messageText) throws JMSException {
        if (!initialized) init();

        Connection connection = null;
        Session session = null;
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue(queueName);
            MessageProducer producer = session.createProducer(destination);
            TextMessage message = session.createTextMessage(messageText);
            producer.send(message);
            System.out.println("Sent message to " + queueName + " (" + messageText.length() + " chars)");
        } finally {
            if (session != null) try { session.close(); } catch (Exception e) { /* ignore */ }
            if (connection != null) try { connection.close(); } catch (Exception e) { /* ignore */ }
        }
    }

    /**
     * Receive a text message from the specified queue (blocking with timeout).
     * 
     * @param timeoutMs how long to wait (0 = forever, which is a bad idea but
     *                  nobody reads this javadoc anyway)
     */
    public static String receiveMessage(String queueName, long timeoutMs) throws JMSException {
        if (!initialized) init();

        Connection connection = null;
        Session session = null;
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue(queueName);
            MessageConsumer consumer = session.createConsumer(destination);

            javax.jms.Message message;
            if (timeoutMs > 0) {
                message = consumer.receive(timeoutMs);
            } else {
                message = consumer.receive();
            }

            if (message != null && message instanceof TextMessage) {
                return ((TextMessage) message).getText();
            }
            return null;
        } finally {
            if (session != null) try { session.close(); } catch (Exception e) { /* ignore */ }
            if (connection != null) try { connection.close(); } catch (Exception e) { /* ignore */ }
        }
    }

    /**
     * Get the connection factory for advanced usage (e.g., message listeners).
     */
    public static ConnectionFactory getConnectionFactory() {
        if (!initialized) init();
        return connectionFactory;
    }
}
