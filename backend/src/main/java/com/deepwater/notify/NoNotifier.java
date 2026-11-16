package com.deepwater.notify;

/** Used when {@code NOTIFY_CHANNEL=none}, the default: notifications are switched off. */
class NoNotifier implements Notifier {
    @Override
    public void send(String text) {
        // Switched off on purpose.
    }

    @Override
    public boolean isConfigured() {
        return false;
    }
}
