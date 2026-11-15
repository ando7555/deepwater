package com.deepwater.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

class SmtpEmailSenderTest {
    @Test
    void sendsAPlainTextMessageFromTheConfiguredAddress() {
        var mail = mock(JavaMailSender.class);
        new SmtpEmailSender(mail, "hello@deepwater.test", true).send("learner@example.test", "Subject", "Body text");
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getFrom()).isEqualTo("hello@deepwater.test");
        assertThat(sent.getValue().getTo()).containsExactly("learner@example.test");
        assertThat(sent.getValue().getSubject()).isEqualTo("Subject");
        assertThat(sent.getValue().getText()).isEqualTo("Body text");
    }

    @Test
    void skipsSendingWhenNotConfigured() {
        var mail = mock(JavaMailSender.class);
        var sender = new SmtpEmailSender(mail, "", true);
        assertThat(sender.isConfigured()).isFalse();
        sender.send("learner@example.test", "Subject", "Body");
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void aRejectedEmailDoesNotBreakTheCaller() {
        var mail = mock(JavaMailSender.class);
        doThrow(new MailSendException("535 authentication failed")).when(mail).send(any(SimpleMailMessage.class));
        assertThatNoException().isThrownBy(() ->
                new SmtpEmailSender(mail, "hello@deepwater.test", true).send("learner@example.test", "S", "B"));
    }

    @Test
    void buildsAProtonStyleStartTlsClientFromSettings() {
        var sender = SmtpEmailSender.fromSettings("smtp.protonmail.ch", 587, "hello@deepwater.test", "token", true, "hello@deepwater.test");
        assertThat(sender.isConfigured()).isTrue();
        var impl = (JavaMailSenderImpl) ReflectionTestUtils.getField(sender, "mail");
        assertThat(impl.getHost()).isEqualTo("smtp.protonmail.ch");
        assertThat(impl.getPort()).isEqualTo(587);
        assertThat(impl.getJavaMailProperties()).containsEntry("mail.smtp.starttls.required", "true").containsEntry("mail.smtp.auth", "true");
        assertThat(SmtpEmailSender.fromSettings("smtp.protonmail.ch", 587, "", "", true, "x@y.z").isConfigured()).isFalse();
    }

    @Test
    void picksTheProviderFromSettingsAndRejectsUnknownOnes() {
        var config = new MailConfig();
        assertThat(config.emailSender("postmark", "a@b.c", "t", "http://x", "h", 587, "", "", true)).isInstanceOf(PostmarkEmailSender.class);
        assertThat(config.emailSender("smtp", "a@b.c", "", "http://x", "h", 587, "u", "p", true)).isInstanceOf(SmtpEmailSender.class);
        assertThat(config.emailSender("none", "", "", "http://x", "h", 587, "", "", true)).isInstanceOf(LogOnlyEmailSender.class);
        assertThatThrownBy(() -> config.emailSender("pigeon", "", "", "http://x", "h", 587, "", "", true))
                .isInstanceOf(IllegalStateException.class);
    }
}
