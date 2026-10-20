package com.iatros.backend.security;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Session-based security for the SPA (decisions.md D-010 to D-013): deny by default, JSON login through
 * {@code AuthController}, CSRF via cookie + header, method security for role checks.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

	static final String[] PUBLIC_AUTH_ENDPOINTS = {
		"/api/v1/auth/login",
		"/api/v1/auth/logout",
		"/api/v1/auth/forgot-password",
		"/api/v1/auth/reset-password",
		"/api/v1/auth/accept-invitation",
	};

	/**
	 * The backend only answers JSON and stored documents, so nothing it returns may load resources, run scripts,
	 * submit forms, or be framed. {@code sandbox} is deliberately absent: it stops browsers' built-in PDF viewers.
	 */
	static final String API_CONTENT_SECURITY_POLICY =
			"default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

	static final String PERMISSIONS_POLICY =
			"camera=(), microphone=(), geolocation=(), payment=(), usb=()";

	/**
	 * Security failures are delegated to the MVC exception resolver so that 401/403 responses use the same
	 * problem format as every other API error (GlobalExceptionHandler).
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
			CookieCsrfTokenRepository csrfTokenRepository, SecurityContextRepository securityContextRepository,
			SecurityLogoutHandler logoutHandler, Clock clock, SecurityProperties properties) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
				.anyRequest().authenticated())
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.csrf(csrf -> csrf
				.csrfTokenRepository(csrfTokenRepository)
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.logout(logout -> logout
				.logoutUrl("/api/v1/auth/logout")
				.addLogoutHandler(logoutHandler)
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives(API_CONTENT_SECURITY_POLICY))
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
				.crossOriginResourcePolicy(corp -> corp.policy(CrossOriginResourcePolicy.SAME_ORIGIN))
				.addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)))
			.addFilterAfter(new AbsoluteSessionTimeoutFilter(clock, properties.sessionMaxLifetime()),
					SecurityContextHolderFilter.class)
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, ex) ->
					exceptionResolver.resolveException(request, response, null, ex))
				.accessDeniedHandler((request, response, ex) ->
					exceptionResolver.resolveException(request, response, null, ex)));
		return http.build();
	}

	/**
	 * The password is verified before the account status, so a wrong password always produces the same result
	 * whether or not the account exists, is invited, deactivated, or belongs to a suspended clinic.
	 */
	@Bean
	AuthenticationManager authenticationManager(IatrosUserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		var provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		provider.setPreAuthenticationChecks(user -> {
		});
		provider.setPostAuthenticationChecks(user -> {
			if (!user.isEnabled()) {
				throw new DisabledException("Account disabled");
			}
		});
		return new ProviderManager(provider);
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository(SecurityProperties properties) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookiePath("/");
		repository.setCookieCustomizer(cookie -> cookie.secure(properties.secureCookies()).sameSite("Lax"));
		return repository;
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** Applied on login: a fresh session ID (fixation protection) and a fresh CSRF token. */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CookieCsrfTokenRepository csrfTokenRepository) {
		var csrfStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
		csrfStrategy.setRequestHandler(new SpaCsrfTokenRequestHandler());
		return new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(),
				csrfStrategy));
	}

}
