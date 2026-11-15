package com.deepwater.platform;

/**
 * Sends one plain-text email. Business code depends only on this interface; which provider delivers the mail
 * (Postmark, any SMTP server such as Proton Mail, or none) is chosen by {@code deepwater.mail.provider}.
 */
public interface EmailSender {
    void send(String toEmail, String subject, String textBody);

    /** Whether this sender can actually deliver mail with the current settings. */
    boolean isConfigured();
}
