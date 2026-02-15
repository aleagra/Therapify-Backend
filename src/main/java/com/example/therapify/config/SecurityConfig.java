    package com.example.therapify.config;

    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.http.HttpMethod;
    import org.springframework.security.authentication.AuthenticationManager;
    import org.springframework.security.config.Customizer;
    import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
    import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
    import org.springframework.security.config.annotation.web.builders.HttpSecurity;
    import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
    import org.springframework.security.config.http.SessionCreationPolicy;
    import org.springframework.security.crypto.password.PasswordEncoder;
    import org.springframework.security.web.SecurityFilterChain;
    import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
    import org.springframework.web.cors.CorsConfiguration;
    import org.springframework.web.cors.CorsConfigurationSource;
    import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

    import java.util.List;

    @Configuration
    @EnableMethodSecurity
    public class SecurityConfig {

        private final PasswordEncoder passwordEncoder;
        private final JwtFilter jwtFilter;

        public SecurityConfig(PasswordEncoder passwordEncoder,
                              JwtFilter jwtFilter) {
            this.passwordEncoder = passwordEncoder;
            this.jwtFilter = jwtFilter;
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration config = new CorsConfiguration();

            config.setAllowedOrigins(List.of("http://localhost:4200"));
            config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS","PATCH"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source =
                    new UrlBasedCorsConfigurationSource();

            source.registerCorsConfiguration("/**", config);
            return source;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

            http
                    .cors(Customizer.withDefaults())
                    .csrf(AbstractHttpConfigurer::disable)

                    .sessionManagement(sm ->
                            sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    )

                    .authorizeHttpRequests(auth -> auth

                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                            .requestMatchers("/auth/**").permitAll()

                            .requestMatchers(HttpMethod.POST, "/usuarios").permitAll()

                            .requestMatchers(HttpMethod.PUT, "/usuarios", "/usuarios/**")
                            .hasAnyRole("PACIENTE","DOCTOR","ADMIN")

                            .requestMatchers(HttpMethod.GET, "/usuarios/**").permitAll()

                            .requestMatchers(HttpMethod.GET, "/doctors/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/reviews/**").permitAll()

                            .requestMatchers(HttpMethod.POST, "/reviews/**")
                            .hasAnyRole("PACIENTE","DOCTOR","ADMIN")

                            .requestMatchers("/appointments/**")
                            .hasAnyRole("PACIENTE","DOCTOR","ADMIN")

                            .requestMatchers("/admin/**").hasRole("ADMIN")

                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                    .formLogin(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable);

            return http.build();
        }

        @Bean
        public AuthenticationManager authenticationManager(
                AuthenticationConfiguration config
        ) throws Exception {
            return config.getAuthenticationManager();
        }
    }
