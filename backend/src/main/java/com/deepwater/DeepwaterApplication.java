package com.deepwater;
import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;import org.springframework.context.annotation.*;import org.springframework.web.servlet.config.annotation.*;
@SpringBootApplication
public class DeepwaterApplication {
    public static void main(String[] args) { SpringApplication.run(DeepwaterApplication.class, args); }

    @Bean
    WebMvcConfigurer cors() {
        return new WebMvcConfigurer() {
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://127.0.0.1:5173", "http://localhost:5173",
                                "http://127.0.0.1:5174", "http://localhost:5174")
                        .allowedMethods("*").allowedHeaders("*");
            }
        };
    }
}
