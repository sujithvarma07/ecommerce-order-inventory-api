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

    @Override
    public void run(String... args) {
        if (appUserRepository.findByUsername(adminUsername).isPresent()) {
            return;
        }

        AppUser admin = new AppUser();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);

        appUserRepository.save(admin);
        log.info("Seeded default admin user '{}'", adminUsername);
    }
}
