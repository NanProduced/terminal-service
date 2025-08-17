package com.colorlight.terminal.application.port.outbound.auth;

/**
 * 密码校验接口
 *
 * @author Nan
 */
public interface EncoderPort {

    /**
     * 验证原始密码与编码密码是否匹配
     *
     * @param rawPassword 原始密码
     * @param encodedPassword 编码密码
     * @return 是否匹配
     */
    boolean matchesByPasswordEncoder(String rawPassword, String encodedPassword);

    /**
     * 编码原始密码
     *
     * @param rawPassword 原始密码
     * @return 编码后的密码
     */
    String encodeByPasswordEncoder(String rawPassword);
}
