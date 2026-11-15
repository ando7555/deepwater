package com.deepwater.notify;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepwater.platform.PlatformService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notify-wiring;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "deepwater.notify.events=signup"})
class OwnerNotificationsWiringTest {
    @Autowired PlatformService platform;
    @MockitoBean Notifier notifier;

    @Test
    void aNewSignUpReachesTheConfiguredChannelWithoutTheEmailAddress() {
        when(notifier.isConfigured()).thenReturn(true);
        String email = "notify-" + UUID.randomUUID() + "@example.test";
        platform.register("Ana", email, "a-long-password-1");
        verify(notifier, timeout(2000)).send("Deepwater: a new account was created (password sign-up).");
        verify(notifier, never()).send(org.mockito.ArgumentMatchers.contains(email));
    }
}
