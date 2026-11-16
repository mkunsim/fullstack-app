package com.example.safari.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.example.safari.security.AdminAuthInterceptor;
import java.util.Objects;

@Configuration
public class CorsConfig {
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Bean
    WebMvcConfigurer corsConfigurer(
            AdminAuthInterceptor adminAuthorizationInterceptor
    ) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(Objects.requireNonNull(adminAuthorizationInterceptor));
            }

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                Objects.requireNonNull(registry);
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:5173",
                        "https://safaritours.dpdns.org",
                        frontendUrl)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true);
            }
        };
    }
}
