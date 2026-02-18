package com.rpacloud.user.service;

import java.util.Optional;

import com.rpacloud.user.entity.User;
import com.rpacloud.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public User createUser(String email, String password, String fullName) {
        User user = User.builder()
                .email(email)
                .hashedPassword(passwordEncoder.encode(password))
                .fullName(fullName)
                .isActive(true)
                .isSuperuser(false)
                .build();
        return userRepository.save(user);
    }

    public Optional<User> authenticate(String email, String password) {
        return userRepository.findByEmail(email)
                .filter(User::getIsActive)
                .filter(user -> passwordEncoder.matches(password, user.getHashedPassword()));
    }

    public long count() {
        return userRepository.count();
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }
}
