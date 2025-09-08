package com.colorlight.terminal.application.port.inbound.program;

/**
 * 设备节目播放相关用例接口
 *
 * @author Nan
 */
public interface TerminalProgramUseCase {

    String getSchedule(Long deviceId);
}
