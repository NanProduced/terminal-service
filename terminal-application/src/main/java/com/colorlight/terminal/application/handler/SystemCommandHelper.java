package com.colorlight.terminal.application.handler;

import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;

import static com.colorlight.terminal.commons.exception.technical.TechErrorCode.INSTANTIATION_IS_PROHIBITED;

/**
 * 内部指令构造器
 *
 * @author Nan
 */
public class SystemCommandHelper {

    /**
     * 私有构造函数，防止实例化
     */
    private SystemCommandHelper() {
        throw new TechnicalException(INSTANTIATION_IS_PROHIBITED);
    }

    /**
     * 构造时间和时区配置上报指令
     * @param deviceId 设备Id
     * @return 指令DTO
     */
    public static SendCommandRequest generateTimeReportCommand(Long deviceId) {
        return SendCommandRequest.builder()
                .deviceId(deviceId)
                .karma(0)
                .authorUrl("api/newrtc.json")
                .contentRaw("")
                .build();
    }
}
