package br.com.lc.nfse.api.auth;

import br.com.lc.nfse.api.tenant.ContaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    ApiKeyAuthFilter apiKeyAuthFilter(ApiKeyService apiKeyService, ApiKeyRepository apiKeyRepository,
                                      ContaRepository contaRepository) {
        return new ApiKeyAuthFilter(apiKeyService, apiKeyRepository, contaRepository);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/dps/preview", "/admin/**").permitAll()
                        .requestMatchers("/v1/**").hasRole("TENANT")
                        .anyRequest().permitAll())
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new EnvelopeErroAuthEntryPoint()));
        return http.build();
    }
}
