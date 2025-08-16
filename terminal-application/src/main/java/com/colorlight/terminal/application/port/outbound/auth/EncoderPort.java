package com.colorlight.terminal.application.port.outbound.auth;

/**
 * 密码校验接口
 *
 * @author Nan
 */
public interface EncoderPort {

    boolean matchesByPasswordEncoder(String rawPassword, String encodePassword);
}
