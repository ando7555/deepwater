package com.deepwater.platform;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the email provider from settings, so switching from Postmark to Proton Mail (or any SMTP server) is a change
 * of environment variables, not of code. {@code MAIL_PROVIDER}: {@code postmark} (default), {@code smtp}, or {@code none}.
 */
@Configuration
class MailConfig {
    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    EmailSender emailSender(
            @Value("${deepwater.mail.provider:postmark}") String provider,
            @Value("${deepwater.mail.from:}") String from,
            @Value("${deepwater.postmark.server-token:}") String postmarkToken,
            @Value("${deepwater.postmark.api-url:https://api.postmarkapp.com/email}") String postmarkUrl,
            @Value("${deepwater.smtp.host:smtp.protonmail.ch}") String smtpHost,
            @Value("${deepwater.smtp.port:587}") int smtpPort,
            @Value("${deepwater.smtp.username:}") String smtpUser,
            @Value("${deepwater.smtp.password:}") String smtpPassword,
            @Value("${deepwater.smtp.starttls:true}") boolean smtpStartTls) {
        EmailSender sender = switch (provider.strip().toLowerCase(Locale.ROOT)) {
            case "postmark" -> new PostmarkEmailSender(postmarkToken, from, postmarkUrl);
            case "smtp", "proton" -> SmtpEmailSender.fromSettings(smtpHost, smtpPort, smtpUser, smtpPassword, smtpStartTls, from);
            case "none", "" -> new LogOnlyEmailSender();
            default -> throw new IllegalStateException("Unknown MAIL_PROVIDER '" + provider + "'. Use postmark, smtp or none.");
        };
        log.info("Email provider: {} ({}).", provider, sender.isConfigured() ? "configured" : "not configured, emails are skipped");
        return sender;
    }
}
