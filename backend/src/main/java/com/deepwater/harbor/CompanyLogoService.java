package com.deepwater.harbor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Company logos for Harbor. The image is the icon each company publishes for its own website, fetched once
 * through Google's public site-icon service and kept in memory. Only companies in {@link CompanyRegistry}
 * can be asked for, so this endpoint cannot be used to make the server fetch arbitrary addresses.
 * People's browsers load the logo from Deepwater, never from Google.
 */
@Service
public class CompanyLogoService {
    private static final Duration RETRY_AFTER = Duration.ofHours(1);
    private final CompanyRegistry registry;
    private final String iconUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, Logo> cache = new ConcurrentHashMap<>();

    public CompanyLogoService(CompanyRegistry registry,
                              @Value("${deepwater.harbor.logo-url:https://www.google.com/s2/favicons?sz=128&domain=}") String iconUrl) {
        this.registry = registry;
        this.iconUrl = iconUrl;
    }

    /** The logo, or null when the company is unknown or has no icon (the screen then shows its initials). */
    public Logo logo(String companyId) {
        CompanyRegistry.Company company = registry.find(companyId);
        if (company == null || company.domain() == null) return null;
        Logo cached = cache.get(companyId);
        if (cached != null && (cached.bytes() != null || cached.checkedAt().plus(RETRY_AFTER).isAfter(Instant.now())))
            return cached.bytes() == null ? null : cached;
        Logo fetched = fetch(company.domain());
        cache.put(companyId, fetched);
        return fetched.bytes() == null ? null : fetched;
    }

    private Logo fetch(String domain) {
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(iconUrl + domain))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            String type = response.headers().firstValue("Content-Type").orElse("image/png");
            if (response.statusCode() != 200 || !type.startsWith("image/") || response.body().length == 0 || response.body().length > 200_000)
                return new Logo(null, null, Instant.now());
            return new Logo(response.body(), type, Instant.now());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return new Logo(null, null, Instant.now());
        } catch (java.io.IOException | RuntimeException _) {
            return new Logo(null, null, Instant.now());
        }
    }

    public record Logo(byte[] bytes, String contentType, Instant checkedAt) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Logo that && java.util.Arrays.equals(bytes, that.bytes)
                    && java.util.Objects.equals(contentType, that.contentType) && java.util.Objects.equals(checkedAt, that.checkedAt);
        }

        @Override
        public int hashCode() {
            return 31 * java.util.Objects.hash(contentType, checkedAt) + java.util.Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "Logo[" + (bytes == null ? 0 : bytes.length) + " bytes, " + contentType + ", " + checkedAt + "]";
        }
    }
}
