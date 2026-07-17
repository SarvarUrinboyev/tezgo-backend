package com.taxi.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    private static final Logger log = Logger.getLogger(SecurityConfig.class.getName());

    @PostConstruct
    public void warnIfCorsOpen() {
        if ("*".equals(allowedOrigins)) {
            log.warning("⚠️ CORS barcha origin'larga ochiq (app.cors.allowed-origins=*). " +
                    "Production uchun aniq domenlarni belgilang!");
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Security headerlar — OWASP tavsiyalari
                .headers(headers -> {
                    headers.frameOptions(fo -> fo.deny());                    // Clickjacking
                    headers.contentTypeOptions(cto -> {});                    // MIME sniffing
                    headers.httpStrictTransportSecurity(hsts -> hsts          // Force HTTPS
                            .includeSubDomains(true)
                            .preload(true)
                            .maxAgeInSeconds(31536000));
                    headers.permissionsPolicy(pp -> pp.policy(               // Browser feature policy
                            "camera=(), microphone=(), geolocation=(self), payment=()"));
                    headers.referrerPolicy(rp -> rp.policy(                  // Referrer leak
                            org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                    .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/verify-passport", "/api/auth/verify-vehicle",
                                "/api/auth/register/driver").hasRole("DRIVER")
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tariffs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/places").permitAll()
                        // Banner (reklama) rasmlari — passenger-app karuselida hammaga ochiq ko'rsatiladi
                        // (HEAD ham ochiq — diagnostika/CDN so'rovlari GET bilan bir xil resursga tegadi)
                        .requestMatchers(HttpMethod.GET, "/api/public/banners/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/public/banners/**").permitAll()
                        // To'lov callback'lari (Payme/Click serverlari chaqiradi)
                        .requestMatchers("/api/payment/payme",
                                "/api/payment/click/prepare",
                                "/api/payment/click/complete").permitAll()
                        // Click ADVANCED SHOP callback'lari (alohida service_id, alohida ledger).
                        .requestMatchers("/api/payment/click-shop/getinfo").permitAll()
                        .requestMatchers("/api/payment/click-shop/**").denyAll()
                        // Swagger — faqat ADMIN yoki dev profilida ochiq
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll() // Prometheus scraper
                        .requestMatchers("/api/chat/**").hasAnyRole("DRIVER", "PASSENGER", "ADMIN")
                        .requestMatchers("/api/promo/validate").hasAnyRole("PASSENGER", "ADMIN")
                        // OPERATOR — faqat operator endpointlari
                        .requestMatchers("/api/operator/**").hasRole("OPERATOR")
                        .requestMatchers("/api/driver/**").hasAnyRole("DRIVER", "ADMIN")
                        .requestMatchers("/api/photos/**").hasAnyRole("DRIVER", "ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/passenger/tariffs").hasAnyRole("PASSENGER", "DRIVER", "ADMIN", "OPERATOR")
                        // DRIVER ham yo'lovchi sifatida buyurtma bera oladi (bitta raqam ikkala ilovada).
                        .requestMatchers("/api/passenger/**").hasAnyRole("PASSENGER", "DRIVER", "ADMIN")
                        .requestMatchers("/api/trips/**").hasAnyRole("DRIVER", "PASSENGER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Production: CORS_ORIGINS env dan aniq domenlar (masalan: https://admin.tezyol.uz)
        // Dev: * (barcha domenlar)
        if ("*".equals(allowedOrigins)) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOriginPatterns(
                    Arrays.asList(allowedOrigins.split(",")));
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
