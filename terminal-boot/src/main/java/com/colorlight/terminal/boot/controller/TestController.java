package com.colorlight.terminal.boot.controller;

import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.CreateTerminalAccountDTO;
import com.colorlight.terminal.rpc.dto.result.TerminalAccountResultDTO;
import com.colorlight.terminal.rpc.service.TerminalAccountRpcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试用接口 - 模拟rpc调用
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final TerminalAccountRpcService terminalAccountRpcService;

    @PostMapping("/createAccount")
    public RpcResult<TerminalAccountResultDTO> testCreateTerminal(@RequestParam("account") String account, @RequestParam("password") String password) {

        CreateTerminalAccountDTO dto = new CreateTerminalAccountDTO(account, password);
        return terminalAccountRpcService.createTerminalAccount(dto);
    }
}
