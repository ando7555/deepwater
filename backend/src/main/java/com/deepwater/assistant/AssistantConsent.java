package com.deepwater.assistant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The person's one-time "yes, send my questions and my Deepwater data to this AI company". Stored per account and
 * per company, so switching to another company asks again. Checked on the server before every run; the screen
 * only asks for it.
 */
@Component
class AssistantConsent {
    private final JdbcTemplate db;

    AssistantConsent(JdbcTemplate db) { this.db = db; }

    boolean given(String accountId, String sharedWith) {
        if (sharedWith == null) return true;
        Integer n = db.queryForObject("select count(*) from assistant_consents where account_id=? and shared_with=?",
                Integer.class, accountId, sharedWith);
        return n != null && n > 0;
    }

    @Transactional
    void grant(String accountId, String sharedWith) {
        db.update("delete from assistant_consents where account_id=?", accountId);
        db.update("insert into assistant_consents(account_id, shared_with) values(?,?)", accountId, sharedWith);
    }

    void withdraw(String accountId) {
        db.update("delete from assistant_consents where account_id=?", accountId);
    }
}
