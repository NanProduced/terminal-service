package com.colorlight.terminal.rpc.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 终端状态报告查询请求DTO
 *
 * @author Demon
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalStatusReportQueryRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备ID（单个查询时使用）
     */
    private Long deviceId;

    /**
     * 设备ID列表（批量查询时使用）
     */
    private List<Long> deviceIds;

    /**
     * 是否包含详细信息
     */
    private Boolean includeDetails;
}
