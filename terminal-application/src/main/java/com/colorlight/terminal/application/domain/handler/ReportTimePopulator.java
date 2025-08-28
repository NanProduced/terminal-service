package com.colorlight.terminal.application.domain.handler;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * 给终端上报数据(led_status)填充reportTime的工具类
 * <P>反射填充所有包含reportTime字段的子结构</P>
 *
 * @author Nan
 */
@Slf4j
public class ReportTimePopulator {

    /**
     * 自动填充TerminalStatusReport中所有非null子对象的reportTime字段
     * @param report 终端状态报告对象
     * @param serverTimestamp 服务器时间戳
     */
    public static void populateReportTime(TerminalStatusReport report, long serverTimestamp) {
        if (Objects.isNull(report)) return;
        // 获取全部字段并处理
        Field[] fields = TerminalStatusReport.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(report);
                // 只处理非null子对象
                if (fieldValue != null) {
                    fillReportTimeIfExist(fieldValue, serverTimestamp);
                }
            } catch (IllegalAccessException e) {
                log.warn("DeviceReport -reportTimePopulate- 处理reportTime字段失败: field={}", field.getName(), e);
            }
        }
    }

    /**
     * 检查对象是否包含reportTime字段，如果包含则填充
     */
    private static void fillReportTimeIfExist(Object obj, long timestamp) {
        try {
            Field reportTimeField = obj.getClass().getDeclaredField("reportTime");
            reportTimeField.setAccessible(true);
            reportTimeField.setLong(obj, timestamp);
        } catch (NoSuchFieldException e) {
            // ignore
            // 该对象没有reportTime字段
        } catch (IllegalAccessException e) {
            log.warn("DeviceReport -reportTimePopulate- 填充reportTime字段失败: class={}", obj.getClass().getName(), e);
        }
    }
}
