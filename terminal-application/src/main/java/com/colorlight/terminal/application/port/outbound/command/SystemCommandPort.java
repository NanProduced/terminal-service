package com.colorlight.terminal.application.port.outbound.command;

/**
 * 系统内部业务需要指令下发接口端口
 *
 * @author Nan
 */
public interface SystemCommandPort {

    /**
     * 请求设备时区信息上报
     * @param deviceId 设备Id
     * @param trigger 触发的beanName
     */
    void requestTimeZoneReport(Long deviceId, String trigger);
}
