package com.colorlight.terminal.application.dto.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResult {

    private boolean success;

    private Long deviceId;

    public static AuthResult success(Long deviceId) {
        return AuthResult.builder()
                .success(true)
                .deviceId(deviceId)
                .build();
    }

    public static AuthResult failed() {
        return AuthResult.builder()
                .success(false)
                .build();
    }
}
