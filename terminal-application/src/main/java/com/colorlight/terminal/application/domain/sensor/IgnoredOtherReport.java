package com.colorlight.terminal.application.domain.sensor;

import java.io.Serial;

/**
 * 该类继承自SensorReport，用于处理除GPS报告之外的其他传感器上报。
 * 当前实现中，对于非GPS类型的上报数据不做任何处理。
 *
 * @see SensorReport
 * @author Nan
 */
public class IgnoredOtherReport extends SensorReport{

    @Serial
    private static final long serialVersionUID = -5050590405528861932L;

    /*
     * 暂时只关注GPS上报，其他上报类型不做处理
     */
}
