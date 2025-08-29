package com.colorlight.terminal.application.handler;

import com.colorlight.terminal.application.dto.request.SendCommandRequest;

/**
 * 内部指令构造器
 *
 * @author Nan
 */
public class SystemCommandHelper {

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
