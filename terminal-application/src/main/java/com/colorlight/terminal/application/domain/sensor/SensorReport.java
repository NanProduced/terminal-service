package com.colorlight.terminal.application.domain.sensor;

import com.fasterxml.jackson.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 传感器上报数据
 * 使用JSON进行序列化和反序列化。
 * 该类作为不同类型传感器报告的基类，其类型由 `sensorType` 字段标识。
 * 本应用已提供了处理GPS报告的具体实现，而其他类型在当前设置中被忽略。
 *
 * @author Nan
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "sensorType",
        visible = true,
        defaultImpl = IgnoredOtherReport.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = GpsReport.class, name = "gps")
        // ...
        // 只处理GPS，其他类型暂不需要
})
@Data
public class SensorReport implements Serializable {

    @Serial
    private static final long serialVersionUID = -256191715665275643L;

    /*=================== 终端上报字段 ===================*/

    /**
     * 终端本地时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("date")
    private LocalDateTime deviceTime;

    /**
     * 传感器数据Id标识
     */
    private Integer sensorId;

    /**
     * 传感器数据类型
     */
    private String sensorType;

    /*=================== 业务添加字段 ===================*/

    /**
     * 设备Id
     */
    @JsonIgnore
    private Long deviceId;

    /**
     * 数据插入时间（服务器时间）
     */
    @JsonIgnore
    private LocalDateTime serverTime;

    /**
     * 验证方法
     * @return 是否为有效数据
     */
    public boolean validate() {
        return true;
    }

}
