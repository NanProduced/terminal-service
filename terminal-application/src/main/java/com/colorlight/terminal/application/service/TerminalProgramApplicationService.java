package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.port.inbound.program.TerminalProgramUseCase;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalProgramApplicationService implements TerminalProgramUseCase {

    private final MainServerRpcPort mainServerRpcPort;

    @Override
    public String getSchedule(Long deviceId) {
        return mainServerRpcPort.getScheduleByDeviceId(deviceId);
    }

    @Override
    public String getProgram(Long deviceId) {
        return mainServerRpcPort.getProgramByDeviceId(deviceId);
    }

    @Override
    public String getMedia(Integer programId, Long deviceId) {
        return mainServerRpcPort.getMediaByProgramId(programId, deviceId);
    }
}
