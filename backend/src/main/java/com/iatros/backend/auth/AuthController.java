package com.iatros.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.auth.dto.ChangePasswordRequest;
import com.iatros.backend.auth.dto.CurrentUserResponse;
import com.iatros.backend.auth.dto.ForgotPasswordRequest;
import com.iatros.backend.auth.dto.LoginRequest;
import com.iatros.backend.auth.dto.TokenPasswordRequest;
import com.iatros.backend.auth.dto.UpdatePreferencesRequest;
import com.iatros.backend.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Authentication endpoints (api.md §5). Logout is handled by Spring Security at POST /api/v1/auth/logout. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final LoginService loginService;
	private final CurrentUserService currentUsers;
	private final PasswordService passwords;

	AuthController(LoginService loginService, CurrentUserService currentUsers, PasswordService passwords) {
		this.loginService = loginService;
		this.currentUsers = currentUsers;
		this.passwords = passwords;
	}

	/** Issues the XSRF-TOKEN cookie. The SPA calls this once on start-up before any state-changing request. */
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login")
	CurrentUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		return loginService.login(request, httpRequest, httpResponse);
	}

	@GetMapping("/me")
	CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		return currentUsers.describe(user);
	}

	@PutMapping("/me/preferences")
	CurrentUserResponse updatePreferences(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody UpdatePreferencesRequest request) {
		return currentUsers.updatePreferences(user, request);
	}

	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
		passwords.changePassword(user, request, httpRequest.getSession().getId());
	}

	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
		passwords.requestPasswordReset(request.email(), httpRequest.getRemoteAddr());
	}

	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@Valid @RequestBody TokenPasswordRequest request, HttpServletRequest httpRequest) {
		passwords.resetPassword(request, httpRequest.getRemoteAddr());
	}

	@PostMapping("/accept-invitation")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void acceptInvitation(@Valid @RequestBody TokenPasswordRequest request, HttpServletRequest httpRequest) {
		passwords.acceptInvitation(request, httpRequest.getRemoteAddr());
	}

}
