package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.MileageAggDocument;
import com.colorlight.terminal.infrastructure.persistence.mongodb.repository.MileageAggregationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncMileageAggListener {

    private final MileageAggregationRepository mileageAggRepository;
    private final TerminalStatsConfigProperties configProperties;

    @EventListener
    @Async("statisticsReportExecutor")
    public void handleMileageAggEvent(MileageAggEvent event) {
        long deviceId = event.getDeviceId();
        List<GpsReport> gpsReports = event.getGpsReports();

        try {
            TreeMap<LocalDateTime, List<GpsReport>> windowGroups = groupByWindow(gpsReports);

            Set<LocalDateTime> windowStarts = windowGroups.keySet();
            Map<LocalDateTime, MileageAggDocument> docMap = batchLoadDocuments(deviceId, windowStarts);

            List<MileageAggDocument> toSave = new ArrayList<>();

            for (Map.Entry<LocalDateTime, List<GpsReport>> entry : windowGroups.entrySet()) {
                LocalDateTime windowStart = entry.getKey();
                List<GpsReport> windowReports = entry.getValue();

                MileageAggDocument doc = processWindow(
                    deviceId, windowStart, windowReports, docMap);

                toSave.add(doc);
            }

            mileageAggRepository.batchSave(toSave);

        } catch (Exception e) {
            log.error("AsyncMileageAgg - 里程聚合失败 - 设备:{}", deviceId, e);
        }
    }

    private TreeMap<LocalDateTime, List<GpsReport>> groupByWindow(List<GpsReport> gpsReports) {
        return gpsReports.stream()
            .collect(Collectors.groupingBy(
                report -> calculateWindowStart(report.getServerTime()),
                TreeMap::new,
                Collectors.toList()
            ));
    }

    private Map<LocalDateTime, MileageAggDocument> batchLoadDocuments(
            Long deviceId, Set<LocalDateTime> windowStarts) {

        List<MileageAggDocument> existingDocs =
            mileageAggRepository.findByDeviceIdAndWindowStartTimeIn(deviceId, windowStarts);

        return existingDocs.stream()
            .collect(Collectors.toMap(
                MileageAggDocument::getWindowStartTime,
                Function.identity()
            ));
    }

    private MileageAggDocument processWindow(
            Long deviceId,
            LocalDateTime windowStart,
            List<GpsReport> windowReports,
            Map<LocalDateTime, MileageAggDocument> docMap) {

        windowReports.sort(Comparator.comparing(GpsReport::getDeviceTime));

        MileageAggDocument doc = docMap.computeIfAbsent(
            windowStart,
            ws -> createNewDocument(deviceId, ws)
        );

        TerminalStatsConfigProperties.Mileage mileageConfig = configProperties.getGps().getMileage();

        if (doc.getLastGpsPoint() == null) {
            doc.setFirstGpsPoint(toGpsPointData(windowReports.get(0)));
            doc.setLastGpsPoint(toGpsPointData(windowReports.get(windowReports.size() - 1)));
            if (windowReports.size() == 1) {
                doc.setGpsPointCount(1);
            } else {
                double totalDistance = 0.0;
                for (int i = 1; i < windowReports.size(); i++) {
                    GpsReport prevReport = windowReports.get(i - 1);
                    GpsReport currReport = windowReports.get(i);
                    totalDistance += calculateEffectiveDistance(prevReport, currReport, mileageConfig);
                }
                doc.setTotalMileage(totalDistance);
                doc.setGpsPointCount(windowReports.size());
            }
        } else {
            MileageAggDocument.GpsPointData prevPoint = doc.getLastGpsPoint();
            double distance = 0.0;
            int index = 0;
            while (index < windowReports.size()) {
                GpsReport currReport = windowReports.get(index);
                MileageAggDocument.GpsPointData currPoint = toGpsPointData(currReport);

                double segmentDistance = calculateDistance(prevPoint, currPoint);
                if (shouldCountDistance(segmentDistance, prevPoint, currPoint, mileageConfig)) {
                    distance += segmentDistance;
                }

                prevPoint = currPoint;
                index++;
            }
            doc.setLastGpsPoint(prevPoint);
            doc.setTotalMileage(doc.getTotalMileage() + distance);
            doc.setGpsPointCount(doc.getGpsPointCount() + windowReports.size());
        }
        return doc;
    }

    private double calculateEffectiveDistance(
            GpsReport prevReport,
            GpsReport currReport,
            TerminalStatsConfigProperties.Mileage config) {

        MileageAggDocument.GpsPointData prevPoint = toGpsPointData(prevReport);
        MileageAggDocument.GpsPointData currPoint = toGpsPointData(currReport);

        double distance = calculateDistance(prevPoint, currPoint);

        if (!shouldCountDistance(distance, prevPoint, currPoint, config)) {
            return 0.0;
        }

        return distance;
    }

    private boolean shouldCountDistance(
            double distanceKm,
            MileageAggDocument.GpsPointData prevPoint,
            MileageAggDocument.GpsPointData currPoint,
            TerminalStatsConfigProperties.Mileage config) {

        if (distanceKm < config.getMinDistanceThresholdKm()) {
            return false;
        }

        if (config.isSpeedFilterEnabled()
                && prevPoint.getSpeed() != null && currPoint.getSpeed() != null
                && prevPoint.getSpeed() < config.getStaticSpeedThreshold()
                && currPoint.getSpeed() < config.getStaticSpeedThreshold()) {
            return false;
        }

        if (config.isAccuracyFilterEnabled()
                && prevPoint.getAccuracy() != null && currPoint.getAccuracy() != null) {
            double accuracySumKm = (prevPoint.getAccuracy() + currPoint.getAccuracy()) / 1000.0;
            if (distanceKm < accuracySumKm) {
                return false;
            }
        }

        return true;
    }

    private MileageAggDocument createNewDocument(Long deviceId, LocalDateTime windowStart) {
        MileageAggDocument doc = new MileageAggDocument();
        doc.setDeviceId(deviceId);
        doc.setWindowStartTime(windowStart);
        doc.setTotalMileage(0.0);
        doc.setGpsPointCount(0);
        doc.setCreateAt(LocalDateTime.now());
        doc.setUpdateAt(LocalDateTime.now());
        return doc;
    }

    private LocalDateTime calculateWindowStart(LocalDateTime gpsTime) {
        try {
            ZonedDateTime utcTime = gpsTime
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC);

            int minute = utcTime.getMinute();
            int windowMinute = (minute / 30) * 30;

            return utcTime
                .withMinute(windowMinute)
                .withSecond(0)
                .withNano(0)
                .toLocalDateTime();
        } catch (Exception e) {
            log.error("AsyncMileageAgg - 时间窗口计算失败: gpsTime={}", gpsTime, e);
            return gpsTime.withMinute(0).withSecond(0).withNano(0);
        }
    }

    private MileageAggDocument.GpsPointData toGpsPointData(GpsReport report) {
        MileageAggDocument.GpsPointData pointData = new MileageAggDocument.GpsPointData();
        pointData.setLatitude(report.getLatitude());
        pointData.setLongitude(report.getLongitude());
        pointData.setReportTime(report.getDeviceTime());
        pointData.setSpeed(report.getSpeed());
        pointData.setAccuracy(report.getAccuracy());
        return pointData;
    }

    private double calculateDistance(
            MileageAggDocument.GpsPointData p1,
            MileageAggDocument.GpsPointData p2) {

        double radiansLat1 = Math.toRadians(p1.getLatitude());
        double radiansLon1 = Math.toRadians(p1.getLongitude());
        double radiansLat2 = Math.toRadians(p2.getLatitude());
        double radiansLon2 = Math.toRadians(p2.getLongitude());

        double dLon = radiansLon2 - radiansLon1;
        double dLat = radiansLat2 - radiansLat1;
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(radiansLat1) * Math.cos(radiansLat2) * Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return 6371.0 * c;
    }
}
