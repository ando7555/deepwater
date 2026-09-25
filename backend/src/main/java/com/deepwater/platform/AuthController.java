package com.deepwater.platform;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final PlatformService platform;
    public AuthController(PlatformService platform) { this.platform = platform; }
    @PostMapping("/register")
    Map<String, String> register(@RequestBody Credentials body) {
        return platform.register(body.name(), body.email(), body.password());
    }
    @PostMapping("/login")
    Map<String, String> login(@RequestBody Credentials body) {
        return platform.login(body.email(), body.password());
    }
    record Credentials(String name, String email, String password) {}
}
