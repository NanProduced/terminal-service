package com.colorlight.terminal.application.service;

import org.apache.dubbo.config.annotation.DubboReference;
import com.colorlight.ccloud.command.interfaces.DeviceReportRpcService;
import org.springframework.stereotype.Service;

/**
 * @author Demon
 * @className com.colorlight.terminal.application.service DeviceReportService
 * @date 4/9/2025 上午11:09
 * @description TODO
 */
@Service
public class DeviceReportService {


    @DubboReference(
            version = "1.0.0",
            group = "terminal",
            timeout = 3000,
            retries = 0,
            check = false
    )
    private DeviceReportRpcService deviceReportRpcService;

    public void reportDeviceStatus() {
        Long deviceId = 1L;
        Long reportTime = 2L;
        String clientIp="127.0.0.1";
        String reportSource = "HTTP";
        deviceReportRpcService.reportDeviceHeartbeat(deviceId, reportTime, clientIp, reportSource);
    }
}
