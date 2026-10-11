package com.kasa.estate_management.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()

                // Only admins can create user accounts
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").hasRole("ADMIN")

                // Billing job triggers: admin only
                .requestMatchers("/api/v1/billing/**").hasRole("ADMIN")

                // Meter readings: field staff can record them
                .requestMatchers(HttpMethod.POST, "/api/v1/meter-readings")
                    .hasAnyRole("ADMIN", "PROPERTY_MANAGER", "CARETAKER")

                // Invoices: read for finance/management, write for admin/accountant
                .requestMatchers(HttpMethod.GET, "/api/v1/invoices/**")
                    .hasAnyRole("ADMIN", "PROPERTY_MANAGER", "ACCOUNTANT")
                .requestMatchers("/api/v1/invoices/**")
                    .hasAnyRole("ADMIN", "ACCOUNTANT")

                // Properties and units: everyone on staff can read, managers can change
                .requestMatchers(HttpMethod.GET, "/api/v1/properties/**", "/api/v1/units/**")
                    .hasAnyRole("ADMIN", "PROPERTY_MANAGER", "ACCOUNTANT", "CARETAKER")
                .requestMatchers("/api/v1/properties/**", "/api/v1/units/**")
                    .hasAnyRole("ADMIN", "PROPERTY_MANAGER")

                // Tenants and leases
                .requestMatchers(HttpMethod.GET, "/api/v1/tenants/**", "/api/v1/leases/**")
                    .hasAnyRole("ADMIN", "PROPERTY_MANAGER", "ACCOUNTANT")
                .requestMatchers("/api/v1/tenants/**", "/api/v1/leases/**")
                    .hasAnyRole("ADMIN", "PROPERTY_MANAGER")

                // Anything not listed above: admin only
                .anyRequest().hasRole("ADMIN")
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}