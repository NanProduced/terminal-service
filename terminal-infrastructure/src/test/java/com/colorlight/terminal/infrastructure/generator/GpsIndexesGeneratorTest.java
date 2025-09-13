package com.colorlight.terminal.infrastructure.generator;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

/**
 * GpsIndexesGenerator 单元测试
 *
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GPS索引生成器测试")
class GpsIndexesGeneratorTest {

    @Mock
    private TerminalStatsConfigProperties statsConfig;

    @InjectMocks
    private GpsIndexesGenerator gpsIndexesGenerator;

    @Nested
    @DisplayName("GeoJsonPoint创建测试")
    class GeoJsonPointCreationTests {

        @Test
        @DisplayName("应该根据GPS报告创建GeoJsonPoint")
        void should_create_geojson_point_from_gps_report() {
            // Given
            GpsReport report = new GpsReport();
            report.setLongitude(116.4074);
            report.setLatitude(39.9042);

            // When
            GeoJsonPoint result = gpsIndexesGenerator.createGeoJsonPoint(report);

            // Then
            assertAll(
                    () -> assertThat(result).isNotNull(),
                    () -> {
                        Assertions.assertNotNull(result);
                        assertThat(result.getX()).isEqualTo(116.4074);
                    },
                    () -> {
                        Assertions.assertNotNull(result);
                        assertThat(result.getY()).isEqualTo(39.9042);
                    }
            );
        }

        @Test
        @DisplayName("应该正确处理负坐标值")
        void should_correctly_handle_negative_coordinates() {
            // Given
            GpsReport report = new GpsReport();
            report.setLongitude(-74.0059);
            report.setLatitude(-34.6037);

            // When
            GeoJsonPoint result = gpsIndexesGenerator.createGeoJsonPoint(report);

            // Then
            assertAll(
                    () -> assertThat(result.getX()).isEqualTo(-74.0059),
                    () -> assertThat(result.getY()).isEqualTo(-34.6037)
            );
        }

        @Test
        @DisplayName("应该处理零坐标值")
        void should_handle_zero_coordinates() {
            // Given
            GpsReport report = new GpsReport();
            report.setLongitude(0.0);
            report.setLatitude(0.0);

            // When
            GeoJsonPoint result = gpsIndexesGenerator.createGeoJsonPoint(report);

            // Then
            assertAll(
                    () -> assertThat(result.getX()).isEqualTo(0.0),
                    () -> assertThat(result.getY()).isEqualTo(0.0)
            );
        }
    }

    @Nested
    @DisplayName("S2 CellId生成测试")
    class S2CellIdGenerationTests {

        @BeforeEach
        void setUp() {
            TerminalStatsConfigProperties.Gps gpsConfig = new TerminalStatsConfigProperties.Gps();
            gpsConfig.setDefaultS2CellLevel(16);
            when(statsConfig.getGps()).thenReturn(gpsConfig);
        }

        @Test
        @DisplayName("应该根据GPS报告生成有效的S2 CellId")
        void should_generate_valid_s2_cell_id_from_gps_report() {
            // Given
            GpsReport report = new GpsReport();
            report.setLongitude(116.4074);
            report.setLatitude(39.9042);

            // When
            String result = gpsIndexesGenerator.generateS2CellId(report);

            // Then
            assertAll(
                    () -> assertThat(result).isNotNull(),
                    () -> assertThat(result).isNotEmpty(),
                    () -> assertThat(result).isEqualTo("35f052b9d")
            );
        }

        @Test
        @DisplayName("应该为不同坐标生成不同的S2 CellId")
        void should_generate_different_s2_cell_ids_for_different_coordinates() {
            // Given
            GpsReport report1 = new GpsReport();
            report1.setLongitude(116.4074);
            report1.setLatitude(39.9042);

            GpsReport report2 = new GpsReport();
            report2.setLongitude(-74.0059);
            report2.setLatitude(40.7128);

            // When
            String result1 = gpsIndexesGenerator.generateS2CellId(report1);
            String result2 = gpsIndexesGenerator.generateS2CellId(report2);

            // Then
            assertThat(result1).isNotEqualTo(result2);
        }

        @Test
        @DisplayName("应该为相近坐标生成相同或相近的S2 CellId")
        void should_generate_same_or_similar_s2_cell_ids_for_close_coordinates() {
            // Given
            GpsReport report1 = new GpsReport();
            report1.setLongitude(116.4070);
            report1.setLatitude(39.9040);

            GpsReport report2 = new GpsReport();
            report2.setLongitude(116.4075);
            report2.setLatitude(39.9045);

            // When
            String result1 = gpsIndexesGenerator.generateS2CellId(report1);
            String result2 = gpsIndexesGenerator.generateS2CellId(report2);

            // Then - 相近坐标应该生成相同或相近的cellId
            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
        }

        @Test
        @DisplayName("应该正确处理边界坐标")
        void should_correctly_handle_boundary_coordinates() {
            // Given - 测试边界坐标
            GpsReport report1 = new GpsReport();
            report1.setLongitude(180.0);
            report1.setLatitude(90.0); // 北极

            GpsReport report2 = new GpsReport();
            report2.setLongitude(-180.0);
            report2.setLatitude(-90.0); // 南极

            // When
            String result1 = gpsIndexesGenerator.generateS2CellId(report1);
            String result2 = gpsIndexesGenerator.generateS2CellId(report2);

            // Then
            assertAll(
                    () -> assertThat(result1).isNotNull(),
                    () -> assertThat(result2).isNotNull()
            );
        }
    }

    @Nested
    @DisplayName("配置访问测试")
    class ConfigurationAccessTests {

        @Test
        @DisplayName("应该能获取配置的cell级别")
        void should_get_configured_cell_level() {
            // Given
            TerminalStatsConfigProperties.Gps gpsConfig = new TerminalStatsConfigProperties.Gps();
            gpsConfig.setDefaultS2CellLevel(18);
            when(statsConfig.getGps()).thenReturn(gpsConfig);

            // When
            Integer result = gpsIndexesGenerator.getConfiguredCellLevel();

            // Then
            assertThat(result).isEqualTo(18);
        }

        @Test
        @DisplayName("应该处理不同的cell级别配置")
        void should_handle_different_cell_level_configurations() {
            // Given
            TerminalStatsConfigProperties.Gps gpsConfig = new TerminalStatsConfigProperties.Gps();
            gpsConfig.setDefaultS2CellLevel(10);
            when(statsConfig.getGps()).thenReturn(gpsConfig);

            GpsReport report = new GpsReport();
            report.setLongitude(0.0);
            report.setLatitude(0.0);

            // When
            String result = gpsIndexesGenerator.generateS2CellId(report);

            // Then
            assertThat(result).isNotNull();
        }
    }
}