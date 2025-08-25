package com.colorlight.terminal.api;

import com.colorlight.terminal.dto.command.DeviceApiCommand;
import com.colorlight.terminal.dto.command.DeviceApiCommandConfirm;
import com.colorlight.terminal.dto.media.DeviceApiMedia;
import com.colorlight.terminal.dto.program.DeviceApiProgram;
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
                                       @RequestParam(value = "device_num") String device_num);

    @PostMapping("/wp-json/wp/v2/comments")
    void confirmCommand(@RequestParam("post") Integer post,
                        @RequestBody DeviceApiCommandConfirm commandConfirm);

    @GetMapping("/wp-json/wp/v2/programs")
    List<DeviceApiProgram> getPrograms(@RequestParam(value = "clt_type", defaultValue = "terminal") String clt_type);

    @GetMapping("/wp-json/wp/v2/media")
    List<DeviceApiMedia> getMedia(@RequestParam(value = "parent") Integer parent);


}
