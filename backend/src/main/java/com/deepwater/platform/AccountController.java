package com.deepwater.platform;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * The person's own account: profile photos, a download of their data, and signing out everywhere. The text fields
 * of the profile are edited through GraphQL ({@code updateMyProfile}); account deletion is {@code DELETE /api/v1/auth/account}.
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private static final String AUTH = "Authorization";
    private final PlatformService platform;
    private final PersonPhotoService photos;
    private final AccountService accounts;

    public AccountController(PlatformService platform, PersonPhotoService photos, AccountService accounts) {
        this.platform = platform;
        this.photos = photos;
        this.accounts = accounts;
    }

    @GetMapping("/photos")
    List<PersonPhotoService.Photo> photos(@RequestHeader(value = AUTH, required = false) String auth) {
        return photos.list(platform.requireAccount(auth));
    }

    /** Upload one photo as multipart field "photo". It is re-encoded, so its metadata is dropped. */
    @PostMapping("/photos")
    PersonPhotoService.Photo upload(@RequestHeader(value = AUTH, required = false) String auth, @RequestPart("photo") MultipartFile file)
            throws IOException {
        String accountId = platform.requireAccount(auth);
        return photos.add(accountId, file.getBytes());
    }

    @DeleteMapping("/photos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        photos.remove(platform.requireAccount(auth), id);
    }

    @PostMapping("/photos/{id}/first")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void makeFirst(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        photos.makeFirst(platform.requireAccount(auth), id);
    }

    /** The person's own photo, for their profile page. */
    @GetMapping("/photos/{id}")
    ResponseEntity<byte[]> photo(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        return jpeg(photos.bytes(platform.requireAccount(auth), id));
    }

    /** Everything Deepwater keeps about this account, as a JSON file. */
    @GetMapping("/export")
    ResponseEntity<Map<String, Object>> export(@RequestHeader(value = AUTH, required = false) String auth) {
        Map<String, Object> data = accounts.export(platform.requireAccount(auth));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"deepwater-data-" + LocalDate.now() + ".json\"")
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON).body(data);
    }

    /** Signs this account out on every device, including this one. */
    @PostMapping("/sign-out-everywhere")
    Map<String, Integer> signOutEverywhere(@RequestHeader(value = AUTH, required = false) String auth) {
        return Map.of("sessionsEnded", accounts.signOutEverywhere(platform.requireAccount(auth)));
    }

    /** A photo as image/jpeg that only the signed-in viewer's browser may keep for a few minutes. */
    public static ResponseEntity<byte[]> jpeg(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff").body(bytes);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> tooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "The photo must be 5 MB or smaller."));
    }
}
