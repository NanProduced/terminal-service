package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MileageAggEvent {

    private Long deviceId;

    private List<GpsReport> gpsReports;

}
