package com.deepwater.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when {@code deepwater.mail.provider=none}: records that an email would have gone out, without its content. */
class LogOnlyEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LogOnlyEmailSender.class);

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void send(String toEmail, String subject, String textBody) {
        log.info("Email sending is switched off (MAIL_PROVIDER=none); not sent: \"{}\".", subject);
    }
}
