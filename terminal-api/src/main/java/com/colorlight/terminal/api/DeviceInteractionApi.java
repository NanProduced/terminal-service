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

    /**
     * 终端信息上报接口
     * @param report 上报数据
     */
    @PutMapping(value = "/wp-json/screen/v1/status", produces = "application/json;charset=UTF-8")
    void reportTerminalStatus(@RequestBody String report);

    /**
     * 终端获取待执行指令列表
     * @param clt_type 无意义
     * @param device_num 无意义
     * @return
     */
    @GetMapping("/wp-json/wp/v2/comments")
    List<DeviceApiCommand> getCommands(@RequestParam(value = "clt_type", defaultValue = "terminal") String clt_type,
                                       @RequestParam(value = "device_num") String device_num);

    /**
     * 终端指令确认
     * @param post 无意义
     * @param commandConfirm 指令确认封装
     */
    @PostMapping("/wp-json/wp/v2/comments")
    void confirmCommand(@RequestParam("post") Integer post,
                        @RequestBody DeviceApiCommandConfirm commandConfirm);

    /**
     * 终端获取节目
     * @param clt_type 无意义
     * @return 节目封装
     */
    @GetMapping("/wp-json/wp/v2/programs")
    List<DeviceApiProgram> getPrograms(@RequestParam(value = "clt_type", defaultValue = "terminal") String clt_type);

    /**
     * 终端获取素材
     * @param parent 素材Id
     * @return 素材信息
     */
    @GetMapping("/wp-json/wp/v2/media")
    List<DeviceApiMedia> getMedia(@RequestParam(value = "parent") Integer parent);

    /**
     * 终端上报素材播放信息
     * @param report 素材播放信息
     */
    @PostMapping( "/wp-json/led/flowfee")
    void reportMediaPlayTimes(@RequestBody String report);

    /**
     * 终端上报节目播放信息
     * @param report 节目播放信息
     */
    @PostMapping("/wp-json/led/flowfee/v2/program")
    void reportProgramPlayTimes(@RequestBody String report);

    /**
     * 终端获取排程信息
     * @return 排程JSON
     */
    @GetMapping("/wp-json/wp/v3/schedules")
    String getSchedule();

    /**
     * 终端上报传感器监控数据
     * @param report 监控数据
     */
    @PostMapping("/wp-json/led/v2/monitor")
    void reportSensorData(@RequestBody String report);

}
