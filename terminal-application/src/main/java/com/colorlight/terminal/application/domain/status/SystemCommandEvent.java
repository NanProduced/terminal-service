package com.colorlight.terminal.application.domain.status;

import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统内部指令下发需求事件
 *
 * @author Nan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SystemCommandEvent {

    private String triggerBeanName;

    private SendCommandRequest command;

    private BusinessScene businessScene;


    public enum BusinessScene {

        /**
         * 设备元数据
         */
        DEVICE_META_DATA;
    }
}
