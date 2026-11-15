package com.deepwater.notify;

import com.deepwater.amber.AmberReportFiled;
import com.deepwater.platform.AccountRegistered;
import java.util.Set;
import java.util.concurrent.Executor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the owner about events that need a human: a new sign-up and a new Amber report waiting for review.
 * Messages carry no names, emails or report notes, because they leave Deepwater for a third-party service.
 * They are sent only after the database change is committed.
 */
public class OwnerNotifications {
    static final String SIGNUP = "signup";
    static final String AMBER_REPORT = "amber-report";

    private final Notifier notifier;
    private final Set<String> enabled;
    private final Executor executor;

    OwnerNotifications(Notifier notifier, Set<String> enabled, Executor executor) {
        this.notifier = notifier;
        this.enabled = Set.copyOf(enabled);
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onAccountRegistered(AccountRegistered event) {
        notify(SIGNUP, "Deepwater: a new account was created (" + event.method() + " sign-up).");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onAmberReport(AmberReportFiled event) {
        notify(AMBER_REPORT, "Deepwater Amber: a new report (" + event.reason() + ") is waiting for review.");
    }

    private void notify(String event, String text) {
        if (!enabled.contains(event) || !notifier.isConfigured()) return;
        executor.execute(() -> notifier.send(text));
    }
}
