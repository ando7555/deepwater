package com.deepwater.notify;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the notification channel and which events are sent, from settings only:
 * {@code NOTIFY_CHANNEL} = {@code none} (default) or {@code signal}; {@code NOTIFY_EVENTS} = comma list of
 * {@code signup}, {@code amber-report}.
 */
@Configuration
class NotifyConfig {
    private static final Logger log = LoggerFactory.getLogger(NotifyConfig.class);

    @Bean
    Notifier notifier(@Value("${deepwater.notify.channel:none}") String channel,
                      @Value("${deepwater.signal.api-url:}") String signalUrl,
                      @Value("${deepwater.signal.sender:}") String signalSender,
                      @Value("${deepwater.signal.recipients:}") String signalRecipients) {
        Notifier notifier = switch (channel.strip().toLowerCase(Locale.ROOT)) {
            case "signal" -> new SignalNotifier(signalUrl, signalSender, splitList(signalRecipients));
            case "none", "" -> new NoNotifier();
            default -> throw new IllegalStateException("Unknown NOTIFY_CHANNEL '" + channel + "'. Use signal or none.");
        };
        log.info("Owner notifications: {} ({}).", channel, notifier.isConfigured() ? "configured" : "off");
        return notifier;
    }

    @Bean
    OwnerNotifications ownerNotifications(Notifier notifier, @Value("${deepwater.notify.events:signup,amber-report}") String events) {
        Set<String> enabled = splitList(events).stream().map(e -> e.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        // Sending happens off the request thread, so a slow or unreachable Signal service never delays a sign-up.
        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        return new OwnerNotifications(notifier, enabled, executor);
    }

    static List<String> splitList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
