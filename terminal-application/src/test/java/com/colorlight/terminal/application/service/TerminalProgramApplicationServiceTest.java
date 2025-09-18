package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 终端节目应用服务单元测试
 * <p>
 * 测试策略：
 * 1. 覆盖节目调度获取场景
 * 2. 覆盖节目信息获取场景
 * 3. 覆盖素材信息获取场景
 * 4. 验证RPC调用的正确性
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("终端节目服务测试")
class TerminalProgramApplicationServiceTest extends BaseApplicationServiceTest {
    
    @Mock
    private MainServerRpcPort mainServerRpcPort;
    
    @InjectMocks
    private TerminalProgramApplicationService service;
    
    // 测试数据
    private static final String TEST_SCHEDULE = "{\"programId\":1001,\"scheduleTime\":\"2023-01-01 12:00:00\"}";
    private static final String TEST_PROGRAM = "{\"programName\":\"test_program\",\"resolution\":\"1920x1080\"}";
    private static final String TEST_MEDIA = "{\"mediaList\":[{\"id\":1,\"name\":\"media1\"},{\"id\":2,\"name\":\"media2\"}]}";
    private static final Integer TEST_PROGRAM_ID = 1001;

    @Test
    @DisplayName("应该通过RPC端口获取设备节目调度")
    void should_get_schedule_through_rpc_port() {
        // Given
        when(mainServerRpcPort.getScheduleByDeviceId(TEST_DEVICE_ID)).thenReturn(TEST_SCHEDULE);
        
        // When
        String schedule = service.getSchedule(TEST_DEVICE_ID);
        
        // Then
        assertThat(schedule).isEqualTo(TEST_SCHEDULE);
    }
    
    @Test
    @DisplayName("应该通过RPC端口获取设备节目信息")
    void should_get_program_through_rpc_port() {
        // Given
        when(mainServerRpcPort.getProgramByDeviceId(TEST_DEVICE_ID)).thenReturn(TEST_PROGRAM);
        
        // When
        String program = service.getProgram(TEST_DEVICE_ID);
        
        // Then
        assertThat(program).isEqualTo(TEST_PROGRAM);
    }
    
    @Test
    @DisplayName("应该通过RPC端口获取节目素材信息")
    void should_get_media_through_rpc_port() {
        // Given
        when(mainServerRpcPort.getMediaByProgramId(TEST_PROGRAM_ID)).thenReturn(TEST_MEDIA);
        
        // When
        String media = service.getMedia(TEST_PROGRAM_ID);
        
        // Then
        assertThat(media).isEqualTo(TEST_MEDIA);
    }
}