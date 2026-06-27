package com.bigcorp.notifications.email;

import com.bigcorp.common.model.Notification;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Properties;

/**
 * Dispatches email notifications via SMTP.
 * 
 * SMTP config is loaded from notification.properties on the classpath.
 * If SMTP is not configured (or host is empty), falls back to just
 * logging the email content. This is the "dev mode" behavior that was
 * added because developers kept getting "connection refused" errors
 * and filing tickets about it.
 * 
 * HTML email support was added in 2001 for the sales team who wanted
 * "prettier" trade confirmations. The inline CSS is ugly but it works
 * with Outlook 2000 which is what most clients use.
 * 
 * NOTE: The SMTP relay at smtp-internal.bigcorp.com is unreliable
 * during market close (5:00-5:30 PM EST). The retry mechanism in
 * NotificationListener handles this, but if you see a burst of
 * failures around that time, don't panic.
 * 
 * @author Karen
 * @since 1.1
 */
public class EmailDispatcher {

    private String smtpHost;
    private String smtpPort;
    private String fromAddress;
    private boolean devMode;
    private boolean htmlEnabled;

    public EmailDispatcher() {
        loadConfig();
    }

    private void loadConfig() {
        try {
            Properties props = new Properties();
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("notification.properties");

            if (is != null) {
                props.load(is);
                is.close();

                smtpHost = props.getProperty("smtp.host", "");
                smtpPort = props.getProperty("smtp.port", "25");
                fromAddress = props.getProperty("smtp.from", "noreply@bigcorp.com");
                devMode = "true".equals(props.getProperty("dev.mode", "false"));
                htmlEnabled = "true".equals(props.getProperty("email.html.enabled", "true"));
            } else {
                System.out.println("WARN: notification.properties not found, email dispatch in dev mode");
                smtpHost = "";
                smtpPort = "25";
                fromAddress = "noreply@bigcorp.com";
                devMode = true;
                htmlEnabled = false;
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed to load email config: " + e.getMessage());
            devMode = true;
        }
    }

    /**
     * Send an email notification.
     * 
     * @throws Exception if the email could not be sent (will trigger retry)
     */
    public void sendEmail(Notification notif) throws Exception {
        // Build the email body from template
        String emailBody = buildEmailBody(notif);

        if (devMode || smtpHost == null || smtpHost.length() == 0) {
            // Dev mode - just log it
            System.out.println("--- EMAIL (dev mode) ---");
            System.out.println("To: " + notif.getRecipient());
            System.out.println("From: " + fromAddress);
            System.out.println("Subject: " + notif.getSubject());
            System.out.println("Body: " + emailBody);
            System.out.println("--- END EMAIL ---");
            return;
        }

        // Production SMTP send
        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.host", smtpHost);
        mailProps.put("mail.smtp.port", smtpPort);
        // We don't use auth because the relay is on the internal network
        // and "adding auth would break the batch jobs" - Operations team, 2001

        Session session = Session.getInstance(mailProps);

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress));
        message.setRecipients(Message.RecipientType.TO,
                InternetAddress.parse(notif.getRecipient()));
        message.setSubject(notif.getSubject());

        if (htmlEnabled) {
            // HTML email - added for the sales team who wanted "prettier" emails
            String htmlBody = wrapInHtml(emailBody, notif.getSubject());
            message.setContent(htmlBody, "text/html; charset=UTF-8");
        } else {
            message.setText(emailBody);
        }

        // The actual send - this is where it usually fails when the relay is down
        Transport.send(message);

        System.out.println("Email sent successfully to " + notif.getRecipient() 
                + " [" + notif.getNotificationId() + "]");
    }

    /**
     * Build email body from XML template based on notification type.
     * Templates are loaded fresh each time - no caching because
     * "it's fast enough" and we had a bug once where a cached template
     * had stale placeholder values after a hot deploy.
     */
    private String buildEmailBody(Notification notif) {
        String templateFile = getTemplateForType(notif.getType());
        String template = loadTemplate(templateFile);

        if (template == null) {
            // fall back to just using the notification body directly
            return notif.getBody() != null ? notif.getBody() : "";
        }

        // Replace placeholders - yes this is naive string replacement
        // but it works and XSLT was "too complicated" for the team
        StringBuffer result = new StringBuffer(template);
        replacePlaceholder(result, "${orderId}", notif.getOrderId());
        replacePlaceholder(result, "${clientName}", notif.getRecipient());
        replacePlaceholder(result, "${subject}", notif.getSubject());

        // Body may contain additional replacement values from the order system
        // These come in as pipe-delimited in the body field (yes, really)
        if (notif.getBody() != null && notif.getBody().indexOf('|') > 0) {
            String[] parts = split(notif.getBody(), '|');
            if (parts.length >= 1) replacePlaceholder(result, "${symbol}", parts[0]);
            if (parts.length >= 2) replacePlaceholder(result, "${quantity}", parts[1]);
            if (parts.length >= 3) replacePlaceholder(result, "${side}", parts[2]);
            if (parts.length >= 4) replacePlaceholder(result, "${price}", parts[3]);
            if (parts.length >= 5) replacePlaceholder(result, "${reason}", parts[4]);
            if (parts.length >= 6) replacePlaceholder(result, "${amount}", parts[5]);
            if (parts.length >= 7) replacePlaceholder(result, "${settlementDate}", parts[6]);
        }

        return result.toString();
    }

    /**
     * Get the template filename for a notification type.
     */
    private String getTemplateForType(String type) {
        if (Notification.TYPE_ORDER_CONFIRM.equals(type)) {
            return "com/bigcorp/notifications/templates/order_confirm.xml";
        } else if (Notification.TYPE_ORDER_REJECT.equals(type)) {
            return "com/bigcorp/notifications/templates/order_reject.xml";
        } else if (Notification.TYPE_SETTLEMENT.equals(type)) {
            return "com/bigcorp/notifications/templates/settlement.xml";
        } else {
            // TYPE_PRICE_ALERT and anything else - no template, use body directly
            return null;
        }
    }

    /**
     * Load a template from the classpath.
     * Loaded fresh each time (no caching).
     */
    private String loadTemplate(String resourcePath) {
        if (resourcePath == null) return null;

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                System.err.println("WARN: Template not found: " + resourcePath);
                return null;
            }

            // read the whole thing into a string (these are small files)
            StringBuffer sb = new StringBuffer();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, bytesRead));
            }
            is.close();
            return sb.toString();
        } catch (Exception e) {
            System.err.println("ERROR: Failed to load template " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Replace all occurrences of a placeholder in a StringBuffer.
     */
    private void replacePlaceholder(StringBuffer sb, String placeholder, String value) {
        if (value == null) value = "";
        String str = sb.toString();
        int idx = str.indexOf(placeholder);
        while (idx >= 0) {
            sb.replace(idx, idx + placeholder.length(), value);
            str = sb.toString();
            idx = str.indexOf(placeholder, idx + value.length());
        }
    }

    /**
     * Split a string by a delimiter character.
     * We can't use String.split() because... actually we could, but this
     * was written before we upgraded to JDK 1.4 and nobody changed it.
     */
    private String[] split(String str, char delimiter) {
        // count the parts
        int count = 1;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == delimiter) count++;
        }

        String[] parts = new String[count];
        int partIndex = 0;
        int start = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == delimiter) {
                parts[partIndex] = str.substring(start, i);
                partIndex++;
                start = i + 1;
            }
        }
        parts[partIndex] = str.substring(start);
        return parts;
    }

    /**
     * Wrap plain text email body in HTML with inline CSS.
     * Added for the sales team in 2001.
     * The CSS is inline because Outlook strips <style> tags.
     */
    private String wrapInHtml(String bodyText, String subject) {
        StringBuffer html = new StringBuffer();
        html.append("<html><body style=\"font-family: Arial, sans-serif; font-size: 12px; color: #333333;\">");
        html.append("<table width=\"600\" cellpadding=\"10\" cellspacing=\"0\" border=\"0\" ");
        html.append("style=\"border: 1px solid #cccccc;\">");
        html.append("<tr><td style=\"background-color: #003366; color: #ffffff; font-size: 14px; font-weight: bold;\">");
        html.append("BigCorp Trading - ").append(subject != null ? subject : "Notification");
        html.append("</td></tr>");
        html.append("<tr><td style=\"padding: 15px;\">");

        // Convert newlines to <br> tags
        if (bodyText != null) {
            for (int i = 0; i < bodyText.length(); i++) {
                char c = bodyText.charAt(i);
                if (c == '\n') {
                    html.append("<br>");
                } else {
                    html.append(c);
                }
            }
        }

        html.append("</td></tr>");
        html.append("<tr><td style=\"background-color: #f0f0f0; font-size: 10px; color: #666666; padding: 8px;\">");
        html.append("This is an automated message from BigCorp Trading Systems. ");
        html.append("Do not reply to this email. For questions, contact the trading desk.");
        html.append("</td></tr>");
        html.append("</table></body></html>");

        return html.toString();
    }
}
