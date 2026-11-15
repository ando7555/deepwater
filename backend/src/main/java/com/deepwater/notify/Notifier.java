package com.deepwater.notify;

/** Delivers a short text to the people who run Deepwater. The channel (Signal, or none) is chosen by settings. */
public interface Notifier {
    void send(String text);

    boolean isConfigured();
}
