package com.deepwater;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;import org.springframework.context.annotation.*;import org.springframework.web.servlet.config.annotation.*;
@SpringBootApplication
public class DeepwaterApplication {
    public static void main(String[] args) { SpringApplication.run(DeepwaterApplication.class, args); }

    @Bean
    WebMvcConfigurer cors(@Value("${deepwater.cors.allowed-origins:}") String extra) {
        return new WebMvcConfigurer() {
            public void addCorsMappings(CorsRegistry registry) {
                var origins = new java.util.ArrayList<>(java.util.List.of(
                        "http://127.0.0.1:5173", "http://localhost:5173",
                        "http://127.0.0.1:5174", "http://localhost:5174"));
                if (!extra.isBlank()) java.util.Arrays.stream(extra.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).forEach(origins::add);
                registry.addMapping("/**")
                        .allowedOrigins(origins.toArray(String[]::new))
                        .allowedMethods("*").allowedHeaders("*");
            }
        };
    }
}
