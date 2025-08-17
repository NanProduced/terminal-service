package com.colorlight.terminal.api;

import com.colorlight.terminal.dto.command.DeviceApiCommand;
import com.colorlight.terminal.dto.command.DeviceApiCommandConfirm;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备交互API
 * <p>基于设备二次开发文档</p>
 *
 * @author Nan
 */
public interface DeviceInteractionApi {

    @PutMapping(value = "/wp-json/screen/v1/status", produces = "application/json;charset=UTF-8")
    void reportTerminalStatus(@RequestBody String report);

    @GetMapping("/wp-json/wp/v2/comments")
    List<DeviceApiCommand> getCommands(@RequestParam(value = "clt_type", defaultValue = "terminal") String clt_type,
                                       @RequestParam(value = "device_num") Integer device_num);

    @PostMapping("/wp-json/wp/v2/comments")
    void confirmCommand(@RequestParam("post") Integer post,
                        @RequestBody DeviceApiCommandConfirm commandConfirm);

}
