package com.colorlight.terminal.boot.controller;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import com.colorlight.terminal.application.dto.result.CommandSendResult;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.service.DeviceReportService;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.CreateTerminalAccountDTO;
import com.colorlight.terminal.rpc.dto.request.SingleCommandRequestDTO;
import com.colorlight.terminal.rpc.dto.result.SingleCommandSendResultDTO;
import com.colorlight.terminal.rpc.dto.result.TerminalAccountResultDTO;
import com.colorlight.terminal.rpc.service.TerminalAccountRpcService;
import com.colorlight.terminal.rpc.service.TerminalCommandRpcService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 测试用接口 - 模拟rpc调用
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final TerminalAccountRpcService terminalAccountRpcService;
    private final DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;
    private final TerminalCommandRpcService terminalCommandRpcService;
    private final DeviceReportService deviceReportService;

    /**
     * 测试创建终端账号
     *
     * @param account
     * @param password
     * @return
     */
    @PostMapping("/createAccount")
    public RpcResult<TerminalAccountResultDTO> testCreateTerminal(@RequestParam("account") String account, @RequestParam("password") String password) {

        CreateTerminalAccountDTO dto = new CreateTerminalAccountDTO(account, password);
        return terminalAccountRpcService.createTerminalAccount(dto);
    }

    /**
     * 测试检查单个设备是否在线
     *
     * @param deviceId
     * @return
     */
    @GetMapping("/online/{deviceId}")
    public ResponseEntity<Boolean> isDeviceOnline(@PathVariable Long deviceId) {
        try {
            boolean online = deviceOnlineStatusUseCase.isDeviceOnline(deviceId);
            return ResponseEntity.ok(online);
        } catch (Exception e) {
            // 故障时返回false（保守策略）
            return ResponseEntity.ok(false);
        }
    }

    /**
     * 测试批量检查设备在线状态
     *
     * @param deviceIds
     * @return
     */
    @PostMapping("/batch-online")
    public ResponseEntity<Map<Long, Boolean>> batchCheckOnline(@RequestBody List<Long> deviceIds) {

        try {
            // 限制批量查询大小，避免性能问题
            if (deviceIds.size() > 1000) {
                deviceIds = deviceIds.subList(0, 1000);
            }
            Map<Long, Boolean> result = deviceOnlineStatusUseCase.batchCheckOnline(deviceIds);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 测试获取设备状态详情
     *
     * @param deviceId
     * @return
     */
    @GetMapping("/detail/{deviceId}")
    public ResponseEntity<DeviceOnlineStatus> getDeviceStatus(@PathVariable Long deviceId) {

        try {
            Optional<DeviceOnlineStatus> statusOpt = deviceOnlineStatusUseCase.getDeviceStatus(deviceId);
            return statusOpt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 批量获取设备状态详情
     *
     * @param deviceIds
     * @return
     */
    @PostMapping("/batch-detail")
    public ResponseEntity<Map<Long, DeviceOnlineStatus>> batchGetDeviceStatus(@RequestBody List<Long> deviceIds) {

        try {
            // 参数验证
            if (deviceIds == null || deviceIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            // 限制批量查询大小
            if (deviceIds.size() > 500) {
                deviceIds = deviceIds.subList(0, 500);
            }
            Map<Long, DeviceOnlineStatus> result = deviceOnlineStatusUseCase.batchGetDeviceStatus(deviceIds);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 测试获取所有在线设备Id
     *
     * @return
     */
    @GetMapping("/online-devices")
    public ResponseEntity<Set<Long>> getOnlineDeviceIds() {

        try {
            Set<Long> onlineDeviceIds = deviceOnlineStatusUseCase.getOnlineDeviceIds();
            return ResponseEntity.ok(onlineDeviceIds);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 测试获取在线设备数量统计
     *
     * @return
     */
    @GetMapping("/online-count")
    public ResponseEntity<Integer> getOnlineDeviceCount() {

        try {
            int count = deviceOnlineStatusUseCase.getOnlineDeviceCount();
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            return ResponseEntity.ok(0); // 故障时返回0
        }
    }

    /**
     * 测试RPC下发单个指令
     *
     * @param request
     * @return
     */
    @PostMapping("/command/single")
    public RpcResult<SingleCommandSendResultDTO> sendCommand(@RequestBody SingleCommandRequestDTO request) {
        return terminalCommandRpcService.sendCommand(request);
    }


    /**
     * 测试RPC上报
     *
     * @return
     */
    @PostMapping("/rpc")
    public RpcResult<Void> rpc() {
        deviceReportService.reportDeviceStatus();
        return RpcResult.success();
    }

}
