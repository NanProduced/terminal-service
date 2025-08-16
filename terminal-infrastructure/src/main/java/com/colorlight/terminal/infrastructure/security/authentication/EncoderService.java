package com.colorlight.terminal.infrastructure.security.authentication;

import com.colorlight.terminal.application.port.outbound.auth.EncoderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EncoderService implements EncoderPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean matchesByPasswordEncoder(String rawPassword, String encodePassword) {
        return passwordEncoder.matches(rawPassword, encodePassword);
    }
}
