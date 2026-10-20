package com.iatros.backend.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.clinic.ClinicStatus;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;

/**
 * Loads login candidates by email. Users without a password (still INVITED) are reported as not found so the
 * provider performs its timing-equalizing dummy hash. Account status is checked only after the password
 * (see SecurityConfig), so a wrong password never reveals whether an account is disabled.
 */
@Service
public class IatrosUserDetailsService implements UserDetailsService {

	private final AppUserRepository users;
	private final ClinicRepository clinics;

	public IatrosUserDetailsService(AppUserRepository users, ClinicRepository clinics) {
		this.users = users;
		this.clinics = clinics;
	}

	@Override
	@Transactional(readOnly = true)
	public AuthenticatedUser loadUserByUsername(String email) {
		AppUser user = users.findByEmail(email)
			.filter(candidate -> candidate.getPasswordHash() != null)
			.orElseThrow(() -> new UsernameNotFoundException("Unknown login"));
		boolean clinicActive = clinics.findById(user.getClinicId())
			.map(clinic -> clinic.getStatus() == ClinicStatus.ACTIVE)
			.orElse(false);
		return new AuthenticatedUser(user.getId(), user.getClinicId(), user.getRole(), user.getPasswordHash(),
				user.canAuthenticate() && clinicActive);
	}

}
