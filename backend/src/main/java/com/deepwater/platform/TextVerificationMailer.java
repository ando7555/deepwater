package com.deepwater.platform;

import org.springframework.stereotype.Component;

/** Writes the verification email and hands it to whichever {@link EmailSender} is configured. */
@Component
class TextVerificationMailer implements VerificationMailer {
    private final EmailSender sender;

    TextVerificationMailer(EmailSender sender) {
        this.sender = sender;
    }

    @Override
    public void sendVerification(String toEmail, String name, String verifyLink) {
        String body = "Hi " + name + ",\n\nConfirm your email address for Deepwater Echo by opening this link:\n\n"
                + verifyLink + "\n\nThe link works once and expires in 24 hours. If you did not sign up, you can ignore this email.\n";
        sender.send(toEmail, "Confirm your email for Deepwater Echo", body);
    }
}
