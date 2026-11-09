package com.meridian.confirmiq.user;

import com.meridian.confirmiq.security.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AppUserRepository users;

    @GetMapping
    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAll(Sort.by("role", "username")).stream()
                .map(u -> new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole(),
                        u.getCounterparty() == null ? null : u.getCounterparty().getLegalName(),
                        u.isActive(), u.getLastLoginAt()))
                .toList();
    }

    public record UserView(Long id, String username, String fullName, String email, Role role,
                           String counterparty, boolean active, Instant lastLoginAt) {}
}
