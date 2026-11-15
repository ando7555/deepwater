package com.deepwater.platform;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Sends email through an SMTP server. Built for Proton Mail's SMTP submission (smtp.protonmail.ch, port 587, STARTTLS,
 * a custom-domain address as user name and an SMTP token as password), but works with any SMTP server.
 */
class SmtpEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mail;
    private final String from;
    private final boolean configured;

    SmtpEmailSender(JavaMailSender mail, String from, boolean configured) {
        this.mail = mail;
        this.from = from == null ? "" : from;
        this.configured = configured && !this.from.isBlank();
    }

    /** Builds the SMTP client from settings. The token is only held in memory and never logged. */
    static SmtpEmailSender fromSettings(String host, int port, String username, String password, boolean startTls, String from) {
        var impl = new JavaMailSenderImpl();
        impl.setHost(host);
        impl.setPort(port);
        impl.setUsername(username);
        impl.setPassword(password);
        impl.setDefaultEncoding("UTF-8");
        Properties props = impl.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        props.put("mail.smtp.starttls.required", String.valueOf(startTls));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        boolean configured = host != null && !host.isBlank() && username != null && !username.isBlank()
                && password != null && !password.isBlank();
        return new SmtpEmailSender(impl, from, configured);
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void send(String toEmail, String subject, String textBody) {
        if (!configured) {
            log.warn("SMTP is not configured (SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD, MAIL_FROM); no email was sent.");
            return;
        }
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(textBody);
        try {
            mail.send(message);
        } catch (MailException ex) {
            log.warn("The SMTP server did not accept an email: {}", ex.getMessage());
        }
    }
}
