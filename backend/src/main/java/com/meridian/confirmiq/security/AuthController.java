package com.meridian.confirmiq.security;

import com.meridian.confirmiq.audit.AuditService;
import com.meridian.confirmiq.user.AppUser;
import com.meridian.confirmiq.user.AppUserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;

    @PostMapping("/login")
    @Transactional
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = users.findByUsernameIgnoreCase(request.username())
                .filter(AppUser::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        user.setLastLoginAt(Instant.now());
        audit.recordLogin(user);
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), UserProfile.of(user));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserProfile me() {
        return users.findByUsernameIgnoreCase(CurrentUser.get().username())
                .map(UserProfile::of)
                .orElseThrow(() -> new BadCredentialsException("unknown user"));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, Instant expiresAt, UserProfile user) {}

    public record UserProfile(String username, String fullName, String email, Role role,
                              Long counterpartyId, String counterpartyName) {
        static UserProfile of(AppUser u) {
            return new UserProfile(u.getUsername(), u.getFullName(), u.getEmail(), u.getRole(),
                    u.getCounterparty() == null ? null : u.getCounterparty().getId(),
                    u.getCounterparty() == null ? null : u.getCounterparty().getLegalName());
        }
    }
}
