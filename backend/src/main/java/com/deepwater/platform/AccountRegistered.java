package com.deepwater.platform;

/**
 * Published after a new Deepwater account is created. Carries no personal data on purpose: listeners such as
 * owner notifications only need to know that it happened and how ({@code "password"} or {@code "google"}).
 */
public record AccountRegistered(String method) {}
