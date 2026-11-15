package com.deepwater.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepwater.amber.AmberReportFiled;
import com.deepwater.platform.AccountRegistered;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OwnerNotificationsTest {
    static class Recording implements Notifier {
        final List<String> sent = new ArrayList<>();
        boolean configured = true;
        @Override public void send(String text) { sent.add(text); }
        @Override public boolean isConfigured() { return configured; }
    }

    @Test
    void sendsOnlyTheEnabledEventsWithoutPersonalData() {
        var signal = new Recording();
        var owner = new OwnerNotifications(signal, Set.of("amber-report"), Runnable::run);
        owner.onAccountRegistered(new AccountRegistered("password"));
        owner.onAmberReport(new AmberReportFiled("FEELS_UNSAFE"));
        assertThat(signal.sent).containsExactly("Deepwater Amber: a new report (FEELS_UNSAFE) is waiting for review.");
    }

    @Test
    void sendsNothingWhenTheChannelIsNotConfigured() {
        var signal = new Recording();
        signal.configured = false;
        new OwnerNotifications(signal, Set.of("signup", "amber-report"), Runnable::run).onAccountRegistered(new AccountRegistered("google"));
        assertThat(signal.sent).isEmpty();
    }

    @Test
    void readsTheChannelAndEventListsFromSettings() {
        var config = new NotifyConfig();
        assertThat(config.notifier("none", "", "", "")).isInstanceOf(NoNotifier.class);
        assertThat(config.notifier("signal", "http://signal:8080", "+1", "+2, +3").isConfigured()).isTrue();
        assertThat(NotifyConfig.splitList(" signup , amber-report ,")).containsExactly("signup", "amber-report");
    }
}
