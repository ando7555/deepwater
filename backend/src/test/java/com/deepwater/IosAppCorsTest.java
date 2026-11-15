package com.deepwater;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The iOS app runs the web frontend from capacitor://localhost, so the API must accept that origin. */
@SpringBootTest
@AutoConfigureMockMvc
class IosAppCorsTest {
    @Autowired MockMvc mvc;

    @Test
    void iosAppOriginMayCallTheApi() throws Exception {
        mvc.perform(options("/graphql")
                        .header("Origin", "capacitor://localhost")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "capacitor://localhost"));
    }

    @Test
    void unknownOriginIsStillRefused() throws Exception {
        mvc.perform(options("/graphql")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
