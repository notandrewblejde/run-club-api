package com.runclub.api.config;

import com.runclub.api.security.JwtAuthLoggingFilter;
import com.runclub.api.security.UserProvisioningFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthLoggingFilter jwtAuthLoggingFilter;

    @Autowired
    private UserProvisioningFilter userProvisioningFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .addFilterBefore(jwtAuthLoggingFilter, UsernamePasswordAuthenticationFilter.class)
            // Runs after the bearer token has populated the SecurityContext so
            // we can read the JWT and JIT-create the local users row before any
            // controller resolves the user by id.
            .addFilterAfter(userProvisioningFilter, BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.GET, "/health", "/api/health").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/v1/admin/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/strava/webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/strava/webhook").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer()
                .jwt();

        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        return http.build();
    }
}
