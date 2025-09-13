package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.DownloadingReport;
import com.colorlight.terminal.application.domain.report.ProgramDownloadingReport;
import com.colorlight.terminal.application.domain.report.UpgradePackageDownloadingReport;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalDownloadingDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

/**
 * MongoDownloadingRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：保存设备下载状态（支持节目下载和升级包下载）
 * 2. 类型区分：根据DownloadingReport的具体类型分别处理
 * 3. 数据更新：如果设备记录已存在则更新，否则创建新记录
 * 4. 时间管理：分别维护节目和升级包的更新时间
 * 5. 操作区分：新记录使用insert，更新记录使用save
 * 6. 日志记录：成功保存时记录debug日志，类型错误时记录error日志
 * <p>
 * 测试策略：
 * - 节目下载状态保存测试（新记录和更新记录）
 * - 升级包下载状态保存测试（新记录和更新记录）
 * - 不支持的报告类型处理测试
 * - 边界条件测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MongoDownloadingRepository单元测试")
class MongoDownloadingRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MongoDownloadingRepository repository;

    private Long deviceId;
    private ProgramDownloadingReport programReport;
    private UpgradePackageDownloadingReport upgradeReport;
    private TerminalDownloadingDocument existingDocument;

    @BeforeEach
    void setUp() {
        deviceId = 12345L;
        
        // 创建节目下载报告
        programReport = new ProgramDownloadingReport();
        programReport.setWhat("program_status");
        
        ProgramDownloadingReport.Downloading programDownloading = new ProgramDownloadingReport.Downloading();
        programDownloading.setDownloadStatusTime(System.currentTimeMillis());
        
        ProgramDownloadingReport.Program program = new ProgramDownloadingReport.Program();
        program.setId(1001);
        program.setName("测试节目");
        
        ProgramDownloadingReport.File file = new ProgramDownloadingReport.File();
        file.setProgramId(1001);
        file.setName("test_file.mp4");
        file.setDownloaded(750L);
        file.setTotal(1000L);
        file.setDownloadUrl("https://example.com/file.mp4");
        
        program.setFiles(List.of(file));
        programDownloading.setPrograms(List.of(program));
        programReport.setDownloading(programDownloading);
        
        // 创建升级包下载报告
        upgradeReport = new UpgradePackageDownloadingReport();
        upgradeReport.setWhat("update_status");
        
        UpgradePackageDownloadingReport.Downloading upgradeDownloading = new UpgradePackageDownloadingReport.Downloading();
        upgradeDownloading.setUpdateStatusTimes(System.currentTimeMillis());
        
        UpgradePackageDownloadingReport.UpdateZip updateZip = new UpgradePackageDownloadingReport.UpdateZip();
        updateZip.setStatus("downloading");
        updateZip.setDesVersion("2.0.0");
        updateZip.setName("upgrade_package.zip");
        updateZip.setDownloaded(500L);
        updateZip.setTotal(1000L);
        updateZip.setProgramId(2001);
        
        upgradeDownloading.setUpdateZip(updateZip);
        upgradeReport.setDownloading(upgradeDownloading);
        
        // 创建已存在的下载文档
        existingDocument = new TerminalDownloadingDocument();
        existingDocument.setDeviceId(deviceId);
        existingDocument.setUpdateAt(LocalDateTime.now().minusHours(1));
    }

    @Nested
    @DisplayName("节目下载状态保存测试")
    class ProgramDownloadingTest {

        @Test
        @DisplayName("应该成功保存新的节目下载状态")
        void should_save_new_program_downloading_status_successfully() {
            // Given
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalDownloadingDocument.class)))
                    .willReturn(null);

            // When
            repository.saveDeviceDownloadingStatus(deviceId, programReport);

            // Then
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalDownloadingDocument.class));
            then(mongoTemplate).should().insert(argThat((Object doc) -> {
                if (doc instanceof TerminalDownloadingDocument downloadingDocument) {
                    return downloadingDocument.getDeviceId().equals(deviceId) &&
                           downloadingDocument.getProgramStatus().equals(programReport) &&
                           downloadingDocument.getProgramUpdateTime() != null &&
                           downloadingDocument.getUpdateAt() != null;
                }
                return false;
            }));
        }

        @Test
        @DisplayName("应该成功更新现有的节目下载状态")
        void should_update_existing_program_downloading_status_successfully() {
            // Given
            LocalDateTime originalUpdateTime = existingDocument.getUpdateAt();
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalDownloadingDocument.class)))
                    .willReturn(existingDocument);

            // When
            repository.saveDeviceDownloadingStatus(deviceId, programReport);

            // Then
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalDownloadingDocument.class));
            then(mongoTemplate).should().save(argThat(doc -> {
                TerminalDownloadingDocument document = (TerminalDownloadingDocument) doc;
                return document.getDeviceId().equals(deviceId) &&
                       document.getProgramStatus().equals(programReport) &&
                       document.getProgramUpdateTime() != null &&
                       document.getUpdateAt().isAfter(originalUpdateTime);
            }));
            
            // 验证文档属性被正确设置
            assertEquals(programReport, existingDocument.getProgramStatus());
            assertNotNull(existingDocument.getProgramUpdateTime());
            assertTrue(existingDocument.getUpdateAt().isAfter(originalUpdateTime));
        }
    }

    @Nested
    @DisplayName("升级包下载状态保存测试")
    class UpgradeDownloadingTest {

        @Test
        @DisplayName("应该成功保存新的升级包下载状态")
        void should_save_new_upgrade_downloading_status_successfully() {
            // Given
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalDownloadingDocument.class)))
                    .willReturn(null);

            // When
            repository.saveDeviceDownloadingStatus(deviceId, upgradeReport);

            // Then
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalDownloadingDocument.class));
            then(mongoTemplate).should().insert(argThat((Object doc) -> {
                if (doc instanceof TerminalDownloadingDocument document) {
                    return document.getDeviceId().equals(deviceId) &&
                           document.getUpgradeStatus().equals(upgradeReport) &&
                           document.getUpgradeUpdateTime() != null &&
                           document.getUpdateAt() != null;
                }
                return false;
            }));
        }

        @Test
        @DisplayName("应该成功更新现有的升级包下载状态")
        void should_update_existing_upgrade_downloading_status_successfully() {
            // Given
            LocalDateTime originalUpdateTime = existingDocument.getUpdateAt();
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalDownloadingDocument.class)))
                    .willReturn(existingDocument);

            // When
            repository.saveDeviceDownloadingStatus(deviceId, upgradeReport);

            // Then
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalDownloadingDocument.class));
            then(mongoTemplate).should().save(argThat(doc -> {
                TerminalDownloadingDocument document = (TerminalDownloadingDocument) doc;
                return document.getDeviceId().equals(deviceId) &&
                       document.getUpgradeStatus().equals(upgradeReport) &&
                       document.getUpgradeUpdateTime() != null &&
                       document.getUpdateAt().isAfter(originalUpdateTime);
            }));
            
            // 验证文档属性被正确设置
            assertEquals(upgradeReport, existingDocument.getUpgradeStatus());
            assertNotNull(existingDocument.getUpgradeUpdateTime());
            assertTrue(existingDocument.getUpdateAt().isAfter(originalUpdateTime));
        }
    }

    @Nested
    @DisplayName("异常和边界情况测试")
    class ExceptionAndEdgeCaseTest {

        @Test
        @DisplayName("应该正确处理不支持的报告类型")
        void should_handle_unsupported_report_type() {
            // Given
            DownloadingReport unsupportedReport = new DownloadingReport() {
                // 自定义的不支持的报告类型
            };

            // When
            repository.saveDeviceDownloadingStatus(deviceId, unsupportedReport);

            // Then
            // 应该查询现有文档但不进行保存操作
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalDownloadingDocument.class));
            then(mongoTemplate).should(never()).save(any(TerminalDownloadingDocument.class));
            then(mongoTemplate).should(never()).insert(any(TerminalDownloadingDocument.class));
        }

        @Test
        @DisplayName("应该正确处理null报告")
        void should_handle_null_report() {
            // When
            repository.saveDeviceDownloadingStatus(deviceId, null);

            // Then
            // 应该查询现有文档但不进行保存操作
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalDownloadingDocument.class));
            then(mongoTemplate).should(never()).save(any(TerminalDownloadingDocument.class));
            then(mongoTemplate).should(never()).insert(any(TerminalDownloadingDocument.class));
        }

        @Test
        @DisplayName("应该正确处理null设备ID")
        void should_handle_null_device_id() {
            // When & Then - 应该不抛出异常
            assertDoesNotThrow(() -> {
                repository.saveDeviceDownloadingStatus(null, programReport);
            });
        }

        @Test
        @DisplayName("应该正确处理负数设备ID")
        void should_handle_negative_device_id() {
            // Given
            Long negativeDeviceId = -123L;
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalDownloadingDocument.class)))
                    .willReturn(null);

            // When & Then
            assertDoesNotThrow(() -> {
                repository.saveDeviceDownloadingStatus(negativeDeviceId, programReport);
            });
        }

        @Test
        @DisplayName("应该正确处理MongoDB查询异常")
        void should_handle_mongodb_query_exception() {
            // Given
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalDownloadingDocument.class)))
                    .willThrow(new RuntimeException("Database connection error"));

            // When & Then - 异常应该被传播
            assertThrows(RuntimeException.class, () -> {
                repository.saveDeviceDownloadingStatus(deviceId, programReport);
            });
        }
    }

    @Nested
    @DisplayName("混合场景测试")
    class MixedScenarioTest {

        @Test
        @DisplayName("应该能够在同一设备上分别保存节目和升级包状态")
        void should_save_both_program_and_upgrade_status_for_same_device() {
            // Given
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalDownloadingDocument.class)))
                    .willReturn(null)  // 第一次查询返回null
                    .willReturn(existingDocument);  // 第二次查询返回已存在的文档

            // When - 先保存节目状态
            repository.saveDeviceDownloadingStatus(deviceId, programReport);
            
            // Then - 验证节目状态被插入
            then(mongoTemplate).should().insert(any(TerminalDownloadingDocument.class));
            
            // When - 再保存升级包状态
            repository.saveDeviceDownloadingStatus(deviceId, upgradeReport);
            
            // Then - 验证升级包状态被更新到现有文档
            then(mongoTemplate).should().save(any(TerminalDownloadingDocument.class));
        }
    }
}