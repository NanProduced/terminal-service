package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
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

    @EventListener
    @Async("statisticsReportExecutor")
    public void handleMileageAggEvent(MileageAggEvent event) {
        long deviceId = event.getDeviceId();
        List<GpsReport> gpsReports = event.getGpsReports();

        try {
            // 2. 按时间窗口分组（内存操作）
            TreeMap<LocalDateTime, List<GpsReport>> windowGroups = groupByWindow(gpsReports);

            // 3. 批量查询聚合文档（一次查询）
            Set<LocalDateTime> windowStarts = windowGroups.keySet();
            Map<LocalDateTime, MileageAggDocument> docMap = batchLoadDocuments(deviceId, windowStarts);

            // 5. 按时间顺序处理每个窗口（内存计算）
            List<MileageAggDocument> toSave = new ArrayList<>();
            
            for (Map.Entry<LocalDateTime, List<GpsReport>> entry : windowGroups.entrySet()) {
                LocalDateTime windowStart = entry.getKey();
                List<GpsReport> windowReports = entry.getValue();
                
                // 处理窗口，返回更新后的文档
                MileageAggDocument doc = processWindow(
                    deviceId, windowStart, windowReports, docMap);
                
                toSave.add(doc);
            }

            // 6. 批量保存（一次保存）
            mileageAggRepository.batchSave(toSave);

                
        } catch (Exception e) {
            log.error("AsyncMileageAgg - 里程聚合失败 - 设备:{}", deviceId, e);
            // 不抛出异常，避免影响其他设备的处理
        }
    }

    /**
     * 按时间窗口分组GPS点
     */
    private TreeMap<LocalDateTime, List<GpsReport>> groupByWindow(List<GpsReport> gpsReports) {
        return gpsReports.stream()
            .collect(Collectors.groupingBy(
                report -> calculateWindowStart(report.getServerTime()),
                TreeMap::new,  // 按时间排序
                Collectors.toList()
            ));
    }

    /**
     * 批量加载聚合文档
     */
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

    /**
     * 处理单个时间窗口
     */
    private MileageAggDocument processWindow(
            Long deviceId,
            LocalDateTime windowStart,
            List<GpsReport> windowReports,
            Map<LocalDateTime, MileageAggDocument> docMap) {
        
        // 按时间排序窗口内的GPS点
        windowReports.sort(Comparator.comparing(GpsReport::getDeviceTime));
        
        // 获取或创建聚合文档
        MileageAggDocument doc = docMap.computeIfAbsent(
            windowStart, 
            ws -> createNewDocument(deviceId, ws)
        );

        // 首次创建时间片聚合文档
        if (doc.getLastGpsPoint() == null) {
            doc.setFirstGpsPoint(toGpsPointData(windowReports.get(0)));
            doc.setLastGpsPoint(toGpsPointData(windowReports.get(windowReports.size() - 1)));
            if (windowReports.size() == 1) {
                doc.setGpsPointCount(1);
            }
            else {
                // 依次对windowReports中的点计算里程
                double totalDistance = 0.0;
                for (int i = 1; i < windowReports.size(); i++) {
                    MileageAggDocument.GpsPointData prevPoint = toGpsPointData(windowReports.get(i - 1));
                    MileageAggDocument.GpsPointData currPoint = toGpsPointData(windowReports.get(i));
                    totalDistance += calculateDistance(prevPoint, currPoint);
                }
                doc.setTotalMileage(totalDistance);
                doc.setGpsPointCount(windowReports.size());
            }
        }

        else {
            MileageAggDocument.GpsPointData prevPoint = doc.getLastGpsPoint();
            double distance = 0.0;
            int index = 0;
            while (index < windowReports.size()) {
                MileageAggDocument.GpsPointData currPoint = toGpsPointData(windowReports.get(index));
                distance += calculateDistance(prevPoint, currPoint);
                prevPoint = currPoint;
                index++;
            }
            doc.setTotalMileage(doc.getTotalMileage() + distance);
            doc.setGpsPointCount(doc.getGpsPointCount() + windowReports.size());
        }
        return doc;
    }

    /**
     * 创建新的聚合文档
     */
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

    /**
     * 计算时间窗口起点（UTC 0时区，30分钟对齐）
     */
    private LocalDateTime calculateWindowStart(LocalDateTime gpsTime) {
        try {
            // 转换为UTC时间
            ZonedDateTime utcTime = gpsTime
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC);
            
            // 计算窗口分钟（0或30）
            int minute = utcTime.getMinute();
            int windowMinute = (minute / 30) * 30;
            
            // 对齐到窗口起点
            return utcTime
                .withMinute(windowMinute)
                .withSecond(0)
                .withNano(0)
                .toLocalDateTime();
        } catch (Exception e) {
            log.error("AsyncMileageAgg - 时间窗口计算失败: gpsTime={}", gpsTime, e);
            // 降级：使用原始时间的小时作为窗口
            return gpsTime.withMinute(0).withSecond(0).withNano(0);
        }
    }

    /**
     * 转换为GpsPointData
     */
    private MileageAggDocument.GpsPointData toGpsPointData(GpsReport report) {
        MileageAggDocument.GpsPointData pointData = new MileageAggDocument.GpsPointData();
        pointData.setLatitude(report.getLatitude());
        pointData.setLongitude(report.getLongitude());
        pointData.setReportTime(report.getDeviceTime());
        return pointData;
    }

    /**
     * 计算两点之间的距离
     */
    private double calculateDistance(
            MileageAggDocument.GpsPointData p1,
            MileageAggDocument.GpsPointData p2) {

        // 将经纬度转换为弧度
        double radiansLat1 = Math.toRadians(p1.getLatitude());
        double radiansLon1 = Math.toRadians(p1.getLongitude());
        double radiansLat2 = Math.toRadians(p2.getLatitude());
        double radiansLon2 = Math.toRadians(p2.getLongitude());

        // Haversine
        double dLon = radiansLon2 - radiansLon1;
        double dLat = radiansLat2 - radiansLat1;
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(radiansLat1) * Math.cos(radiansLat2) * Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // 计算距离
        return 6371.0 * c;
    }
}
