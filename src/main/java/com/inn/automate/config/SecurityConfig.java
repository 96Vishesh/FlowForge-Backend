package com.inn.automate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for API endpoints
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/scraper/**").permitAll() // Allow scraper endpoints
                        // Granting public access to all Gemini Resume endpoints
                        .requestMatchers("/api/resume/**").permitAll()
                        .requestMatchers("/api/resume/health").permitAll()
                        .requestMatchers("/api/resume/transform").permitAll()
                        .requestMatchers("/api/resume/download").permitAll()
                        .requestMatchers("/api/jobs/match").permitAll()
                        .requestMatchers("/api/jobs/health").permitAll()
                        .requestMatchers("/api/resume/generate-pdf").permitAll()
                        .requestMatchers("/api/resume/templates").permitAll()
                        .requestMatchers("/error").permitAll() // Allow error page
                        .anyRequest().authenticated() // Require auth for other endpoints
                );

        return http.build();
    }
}
