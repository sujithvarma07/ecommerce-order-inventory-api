package com.ecommerce.orderinventory.security;

import com.ecommerce.orderinventory.entity.AppUser;
import com.ecommerce.orderinventory.entity.Role;
import com.ecommerce.orderinventory.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds two demo accounts on startup, if they don't already exist:
 * an ADMIN account (catalog management, order fulfillment) and a USER account
 * (placing orders as a regular customer). Both usernames/passwords come from
 * required environment variables — see README.md > Configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-username}")
    private String adminUsername;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Value("${app.security.customer-username}")
    private String customerUsername;

    @Value("${app.security.customer-password}")
    private String customerPassword;

    @Override
    public void run(String... args) {
        seedIfMissing(adminUsername, adminPassword, Role.ADMIN);
        seedIfMissing(customerUsername, customerPassword, Role.USER);
    }

    private void seedIfMissing(String username, String rawPassword, Role role) {
        if (appUserRepository.findByUsername(username).isPresent()) {
            return;
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);

        appUserRepository.save(user);
        log.info("Seeded default {} user '{}'", role, username);
    }
}
