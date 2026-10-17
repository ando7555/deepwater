package com.deepwater.platform;

/** Sends the email that lets a person prove they own the address they signed up with. */
public interface VerificationMailer {
    void sendVerification(String toEmail, String name, String verifyLink);
}
