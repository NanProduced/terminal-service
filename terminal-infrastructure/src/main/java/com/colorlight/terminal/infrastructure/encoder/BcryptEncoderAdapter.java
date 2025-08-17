package com.colorlight.terminal.infrastructure.encoder;

import com.colorlight.terminal.application.port.outbound.auth.EncoderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BcryptEncoderAdapter implements EncoderPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean matchesByPasswordEncoder(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public String encodeByPasswordEncoder(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
