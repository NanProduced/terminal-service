package com.colorlight.terminal.infrastructure.websocket.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebsocketTerminalCommand单元测试
 *
 * 业务逻辑总结：
 * WebsocketTerminalCommand是WebSocket通信的指令对象，用于传输终端指令数据。
 * 它包含指令列表(data)和LED屏ID(ledId)。
 * 核心职责是承载WebSocket通信中的指令信息，支持JSON序列化/反序列化。
 *
 * 主要业务流程：
 * 1. 通过Builder模式构建指令对象
 * 2. 通过JSON序列化/反序列化进行通信
 * 3. JSON属性映射：ledId映射为led_id
 * 4. 支持嵌套的WebsocketCommand和WebsocketContent对象
 *
 * @author Nan
 */
@DisplayName("WebsocketTerminalCommand单元测试")
class WebsocketTerminalCommandTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Builder模式构建对象")
    class BuilderTests {

        @Test
        @DisplayName("应该使用Builder正确构建WebsocketTerminalCommand对象")
        void should_build_websocket_terminal_command_with_builder() {
            // Given - 准备指令数据
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand command =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com/author", 50, content);

            List<WebsocketTerminalCommand.WebsocketCommand> commandList = Arrays.asList(command);
            Integer ledId = 101;

            // When - 使用Builder构建对象
            WebsocketTerminalCommand result = WebsocketTerminalCommand.builder()
                .data(commandList)
                .ledId(ledId)
                .build();

            // Then - 验证对象构建成功
            assertThat(result).isNotNull();
            assertThat(result.getData()).isEqualTo(commandList);
            assertThat(result.getLedId()).isEqualTo(ledId);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("应该正确构建空的指令列表")
        void should_build_command_with_empty_data_list() {
            // Given - 准备空指令列表
            List<WebsocketTerminalCommand.WebsocketCommand> emptyList = Arrays.asList();
            Integer ledId = 102;

            // When - 使用Builder构建对象
            WebsocketTerminalCommand result = WebsocketTerminalCommand.builder()
                .data(emptyList)
                .ledId(ledId)
                .build();

            // Then - 验证对象
            assertThat(result.getData()).isEmpty();
            assertThat(result.getLedId()).isEqualTo(102);
        }

        @Test
        @DisplayName("应该支持Builder中的null值")
        void should_support_null_values_in_builder() {
            // When - 使用Builder构建包含null值的对象
            WebsocketTerminalCommand result = WebsocketTerminalCommand.builder()
                .data(null)
                .ledId(null)
                .build();

            // Then - 验证null值被保存
            assertThat(result.getData()).isNull();
            assertThat(result.getLedId()).isNull();
        }
    }

    @Nested
    @DisplayName("JSON序列化测试")
    class JsonSerializationTests {

        @Test
        @DisplayName("应该正确序列化WebsocketTerminalCommand为JSON")
        void should_serialize_to_json_correctly() throws Exception {
            // Given - 准备指令对象
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand command =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(Arrays.asList(command), 101);

            // When - 序列化为JSON字符串
            String json = objectMapper.writeValueAsString(terminalCommand);

            // Then - 验证JSON格式正确
            assertThat(json).contains("\"led_id\"");
            assertThat(json).contains("\"id\"");
            assertThat(json).contains("\"post\"");
            assertThat(json).contains("\"author_url\"");
            assertThat(json).contains("\"karma\"");
            assertThat(json).contains("\"data\"");
            assertThat(json).contains("101");
        }

        @Test
        @DisplayName("应该正确处理JsonProperty注解 ledId映射为led_id")
        void should_map_ledId_to_led_id_in_json() throws Exception {
            // Given - 准备指令对象
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(null, 999);

            // When - 序列化为JSON
            String json = objectMapper.writeValueAsString(terminalCommand);

            // Then - 验证ledId被映射为led_id
            assertThat(json).contains("\"led_id\":999");
            assertThat(json).doesNotContain("\"ledId\"");
        }

        @Test
        @DisplayName("应该正确处理authorUrl映射为author_url")
        void should_map_authorUrl_to_author_url_in_json() throws Exception {
            // Given - 准备指令对象
            WebsocketTerminalCommand.WebsocketCommand command =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com/auth", 50, null);
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(Arrays.asList(command), 101);

            // When - 序列化为JSON
            String json = objectMapper.writeValueAsString(terminalCommand);

            // Then - 验证authorUrl被映射为author_url
            assertThat(json).contains("\"author_url\"");
            assertThat(json).doesNotContain("\"authorUrl\"");
        }
    }

    @Nested
    @DisplayName("JSON反序列化测试")
    class JsonDeserializationTests {

        @Test
        @DisplayName("应该正确反序列化JSON为WebsocketTerminalCommand")
        void should_deserialize_from_json_correctly() throws Exception {
            // Given - JSON字符串
            String json = """
                {
                    "data": [
                        {
                            "id": 1,
                            "post": 100,
                            "author_url": "http://example.com",
                            "karma": 50,
                            "content": {
                                "raw": "{\\"action\\":\\"play\\"}"
                            }
                        }
                    ],
                    "led_id": 101
                }
                """;

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证反序列化结果
            assertThat(result).isNotNull();
            assertThat(result.getLedId()).isEqualTo(101);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getId()).isEqualTo(1);
            assertThat(result.getData().get(0).getPost()).isEqualTo(100);
            assertThat(result.getData().get(0).getAuthorUrl()).isEqualTo("http://example.com");
            assertThat(result.getData().get(0).getKarma()).isEqualTo(50);
            assertThat(result.getData().get(0).getContent().getRaw()).isEqualTo("{\"action\":\"play\"}");
        }

        @Test
        @DisplayName("应该正确处理led_id到ledId的反序列化映射")
        void should_deserialize_led_id_to_ledId_correctly() throws Exception {
            // Given - JSON字符串
            String json = "{\"led_id\": 999, \"data\": []}";

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证ledId正确映射
            assertThat(result.getLedId()).isEqualTo(999);
        }

        @Test
        @DisplayName("应该正确处理author_url到authorUrl的反序列化映射")
        void should_deserialize_author_url_to_authorUrl_correctly() throws Exception {
            // Given - JSON字符串
            String json = """
                {
                    "data": [
                        {
                            "id": 1,
                            "post": 100,
                            "author_url": "http://example.com/test",
                            "karma": 50
                        }
                    ],
                    "led_id": 101
                }
                """;

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证authorUrl正确映射
            assertThat(result.getData().get(0).getAuthorUrl()).isEqualTo("http://example.com/test");
        }

        @Test
        @DisplayName("应该正确处理空data字段")
        void should_deserialize_empty_data_list() throws Exception {
            // Given - JSON字符串
            String json = "{\"led_id\": 101, \"data\": []}";

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证data为空列表
            assertThat(result.getData()).isEmpty();
        }

        @Test
        @DisplayName("应该正确处理缺少可选字段的JSON")
        void should_deserialize_with_missing_optional_fields() throws Exception {
            // Given - JSON字符串（省略某些可选字段）
            String json = "{\"led_id\": 101}";

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证反序列化成功，缺失字段为null
            assertThat(result.getLedId()).isEqualTo(101);
            assertThat(result.getData()).isNull();
        }
    }

    @Nested
    @DisplayName("Getter方法测试")
    class GetterTests {

        @Test
        @DisplayName("应该正确获取data字段")
        void should_get_data_correctly() {
            // Given - 准备对象
            WebsocketTerminalCommand.WebsocketCommand command =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, null);
            List<WebsocketTerminalCommand.WebsocketCommand> commandList = Arrays.asList(command);
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(commandList, 101);

            // When - 调用getter方法
            List<WebsocketTerminalCommand.WebsocketCommand> result = terminalCommand.getData();

            // Then - 验证返回值
            assertThat(result).isEqualTo(commandList);
        }

        @Test
        @DisplayName("应该正确获取ledId字段")
        void should_get_ledId_correctly() {
            // Given - 准备对象
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(null, 999);

            // When - 调用getter方法
            Integer result = terminalCommand.getLedId();

            // Then - 验证返回值
            assertThat(result).isEqualTo(999);
        }
    }

    @Nested
    @DisplayName("WebsocketCommand嵌套类测试")
    class WebsocketCommandTests {

        @Test
        @DisplayName("应该正确构建WebsocketCommand对象")
        void should_build_websocket_command_correctly() {
            // Given - 准备数据
            Integer id = 1;
            Integer post = 100;
            String authorUrl = "http://example.com";
            Integer karma = 50;
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");

            // When - 构建对象
            WebsocketTerminalCommand.WebsocketCommand command =
                new WebsocketTerminalCommand.WebsocketCommand(id, post, authorUrl, karma, content);

            // Then - 验证对象
            assertThat(command.getId()).isEqualTo(id);
            assertThat(command.getPost()).isEqualTo(post);
            assertThat(command.getAuthorUrl()).isEqualTo(authorUrl);
            assertThat(command.getKarma()).isEqualTo(karma);
            assertThat(command.getContent()).isEqualTo(content);
        }
    }

    @Nested
    @DisplayName("WebsocketContent嵌套类测试")
    class WebsocketContentTests {

        @Test
        @DisplayName("应该正确构建WebsocketContent对象")
        void should_build_websocket_content_correctly() {
            // Given - 准备数据
            String raw = "{\"action\":\"play\",\"data\":\"content\"}";

            // When - 构建对象
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent(raw);

            // Then - 验证对象
            assertThat(content.getRaw()).isEqualTo(raw);
        }

        @Test
        @DisplayName("应该支持空的raw字段")
        void should_support_empty_raw_content() {
            // When - 构建空raw的对象
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("");

            // Then - 验证对象
            assertThat(content.getRaw()).isEmpty();
        }
    }

    @Nested
    @DisplayName("对象相等性和toString测试")
    class EqualsAndToStringTests {

        @Test
        @DisplayName("应该正确生成toString()结果")
        void should_generate_toString_correctly() {
            // Given - 准备对象
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(null, 101);

            // When - 调用toString方法
            String result = terminalCommand.toString();

            // Then - 验证toString包含关键信息
            assertThat(result).contains("WebsocketTerminalCommand");
            assertThat(result).contains("ledId=101");
        }

        @Test
        @DisplayName("两个相同的对象应该相等")
        void should_be_equal_for_same_values() {
            // Given - 准备两个相同的对象
            WebsocketTerminalCommand cmd1 =
                new WebsocketTerminalCommand(null, 101);
            WebsocketTerminalCommand cmd2 =
                new WebsocketTerminalCommand(null, 101);

            // When - 比较对象
            // Then - 验证对象相等（由于使用@Data和Lombok）
            assertThat(cmd1).isEqualTo(cmd2);
        }

        @Test
        @DisplayName("不同的ledId应该导致对象不相等")
        void should_not_be_equal_for_different_ledId() {
            // Given - 准备两个ledId不同的对象
            WebsocketTerminalCommand cmd1 =
                new WebsocketTerminalCommand(null, 101);
            WebsocketTerminalCommand cmd2 =
                new WebsocketTerminalCommand(null, 102);

            // When & Then - 验证对象不相等
            assertThat(cmd1).isNotEqualTo(cmd2);
        }

        @Test
        @DisplayName("相同的对象应该有相同的hashCode")
        void should_have_same_hashCode_for_equal_objects() {
            // Given - 准备两个相同的对象
            WebsocketTerminalCommand cmd1 =
                new WebsocketTerminalCommand(null, 101);
            WebsocketTerminalCommand cmd2 =
                new WebsocketTerminalCommand(null, 101);

            // When & Then - 验证hashCode相同
            assertThat(cmd1.hashCode()).isEqualTo(cmd2.hashCode());
        }

        @Test
        @DisplayName("WebsocketCommand对象的equals应该正确比较")
        void should_compare_websocket_command_objects_correctly() {
            // Given - 准备两个相同的WebsocketCommand对象
            WebsocketTerminalCommand.WebsocketCommand cmd1 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, null);
            WebsocketTerminalCommand.WebsocketCommand cmd2 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, null);

            // When & Then - 验证相等性
            assertThat(cmd1).isEqualTo(cmd2);
            assertThat(cmd1.hashCode()).isEqualTo(cmd2.hashCode());
        }

        @Test
        @DisplayName("WebsocketContent对象的equals应该正确比较")
        void should_compare_websocket_content_objects_correctly() {
            // Given - 准备两个相同的WebsocketContent对象
            WebsocketTerminalCommand.WebsocketContent content1 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketContent content2 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");

            // When & Then - 验证相等性
            assertThat(content1).isEqualTo(content2);
            assertThat(content1.hashCode()).isEqualTo(content2.hashCode());
        }
    }

    @Nested
    @DisplayName("Builder链式调用组合测试")
    class BuilderChainCombinationTests {

        @Test
        @DisplayName("应该支持多个Builder链式调用")
        void should_support_multiple_builder_chain_calls() {
            // Given - 准备多个指令
            WebsocketTerminalCommand.WebsocketContent content1 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand command1 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content1);

            WebsocketTerminalCommand.WebsocketContent content2 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"stop\"}");
            WebsocketTerminalCommand.WebsocketCommand command2 =
                new WebsocketTerminalCommand.WebsocketCommand(2, 200, "http://example2.com", 60, content2);

            List<WebsocketTerminalCommand.WebsocketCommand> commandList = Arrays.asList(command1, command2);

            // When - 使用Builder构建对象
            WebsocketTerminalCommand result = WebsocketTerminalCommand.builder()
                .data(commandList)
                .ledId(999)
                .build();

            // Then - 验证对象
            assertThat(result.getData()).hasSize(2);
            assertThat(result.getData().get(0).getId()).isEqualTo(1);
            assertThat(result.getData().get(1).getId()).isEqualTo(2);
            assertThat(result.getLedId()).isEqualTo(999);
        }

        @Test
        @DisplayName("应该支持Builder重复设置相同字段")
        void should_support_builder_with_repeated_field_setting() {
            // Given & When - 使用Builder多次设置字段
            WebsocketTerminalCommand result = WebsocketTerminalCommand.builder()
                .ledId(101)
                .ledId(102)  // 覆盖前一个值
                .data(null)
                .build();

            // Then - 验证最后一个值被使用
            assertThat(result.getLedId()).isEqualTo(102);
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("应该支持部分字段构建")
        void should_support_partial_field_construction() {
            // Given & When - 只设置某些字段
            WebsocketTerminalCommand result = WebsocketTerminalCommand.builder()
                .ledId(555)
                .build();

            // Then - 验证未设置的字段为null
            assertThat(result.getLedId()).isEqualTo(555);
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("应该支持大数据列表的Builder构建")
        void should_support_builder_with_large_command_list() {
            // Given - 准备大量指令
            List<WebsocketTerminalCommand.WebsocketCommand> commandList = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                WebsocketTerminalCommand.WebsocketContent content =
                    new WebsocketTerminalCommand.WebsocketContent("{\"id\":" + i + "}");
                WebsocketTerminalCommand.WebsocketCommand command =
                    new WebsocketTerminalCommand.WebsocketCommand(i, i * 10, "http://example.com/" + i, i * 5, content);
                commandList.add(command);
            }

            // When - 使用Builder构建对象
            WebsocketTerminalCommand result = WebsocketTerminalCommand.builder()
                .data(commandList)
                .ledId(888)
                .build();

            // Then - 验证所有数据被保存
            assertThat(result.getData()).hasSize(100);
            assertThat(result.getData().get(0).getId()).isEqualTo(0);
            assertThat(result.getData().get(99).getId()).isEqualTo(99);
            assertThat(result.getLedId()).isEqualTo(888);
        }
    }

    @Nested
    @DisplayName("JSON反序列化边界情况测试")
    class JsonDeserializationEdgeCasesTests {

        @Test
        @DisplayName("应该处理特殊字符的JSON内容")
        void should_handle_special_characters_in_json_content() throws Exception {
            // Given - JSON字符串包含特殊字符
            String json = """
                {
                    "data": [
                        {
                            "id": 1,
                            "post": 100,
                            "author_url": "http://example.com?param=\\"value\\"",
                            "karma": 50,
                            "content": {
                                "raw": "{\\"text\\":\\"Hello\\nWorld!\\"}"
                            }
                        }
                    ],
                    "led_id": 101
                }
                """;

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证特殊字符被正确处理
            assertThat(result).isNotNull();
            assertThat(result.getLedId()).isEqualTo(101);
            assertThat(result.getData().get(0).getAuthorUrl()).contains("?");
        }

        @Test
        @DisplayName("应该处理null值字段的JSON")
        void should_handle_null_fields_in_json() throws Exception {
            // Given - JSON字符串包含null值
            String json = "{\"led_id\": 101, \"data\": null}";

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证null值被保留
            assertThat(result.getLedId()).isEqualTo(101);
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("应该处理包含特殊字符的authorUrl")
        void should_handle_special_characters_in_author_url() throws Exception {
            // Given - JSON字符串包含特殊URL字符
            String json = """
                {
                    "data": [
                        {
                            "id": 1,
                            "post": 100,
                            "author_url": "http://example.com/path?key1=value1&key2=value2#anchor",
                            "karma": 50
                        }
                    ],
                    "led_id": 101
                }
                """;

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证URL被正确解析
            assertThat(result.getData().get(0).getAuthorUrl())
                .contains("?").contains("&").contains("#");
        }

        @Test
        @DisplayName("应该处理极端的整数值")
        void should_handle_extreme_integer_values() throws Exception {
            // Given - JSON字符串包含最大最小整数值
            String json = """
                {
                    "data": [
                        {
                            "id": 2147483647,
                            "post": 0,
                            "author_url": "http://example.com",
                            "karma": -2147483648
                        }
                    ],
                    "led_id": 1
                }
                """;

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证极端值被正确处理
            assertThat(result.getData().get(0).getId()).isEqualTo(2147483647);
            assertThat(result.getData().get(0).getKarma()).isEqualTo(-2147483648);
        }

        @Test
        @DisplayName("应该处理空的content对象")
        void should_handle_empty_content_object() throws Exception {
            // Given - JSON字符串包含空的content对象
            String json = """
                {
                    "data": [
                        {
                            "id": 1,
                            "post": 100,
                            "author_url": "http://example.com",
                            "karma": 50,
                            "content": {}
                        }
                    ],
                    "led_id": 101
                }
                """;

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证empty content被处理
            assertThat(result.getData().get(0).getContent()).isNotNull();
            assertThat(result.getData().get(0).getContent().getRaw()).isNull();
        }

        @Test
        @DisplayName("应该处理超长的JSON字符串")
        void should_handle_very_long_json_strings() throws Exception {
            // Given - 准备超长的JSON字符串
            StringBuilder longRaw = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                longRaw.append("x");
            }
            String json = String.format("""
                {
                    "data": [
                        {
                            "id": 1,
                            "post": 100,
                            "author_url": "http://example.com",
                            "karma": 50,
                            "content": {
                                "raw": "%s"
                            }
                        }
                    ],
                    "led_id": 101
                }
                """, longRaw.toString());

            // When - 反序列化JSON
            WebsocketTerminalCommand result = objectMapper.readValue(json, WebsocketTerminalCommand.class);

            // Then - 验证超长字符串被正确处理
            String rawContent = result.getData().get(0).getContent().getRaw();
            assertThat(rawContent.length()).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("构造器和setter组合测试")
    class ConstructorAndSetterCombinationTests {

        @Test
        @DisplayName("应该支持通过构造器初始化所有字段")
        void should_initialize_all_fields_via_constructor() {
            // Given - 准备完整的数据
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand command =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);
            List<WebsocketTerminalCommand.WebsocketCommand> commandList = Arrays.asList(command);

            // When - 通过构造器创建对象
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(commandList, 101);

            // Then - 验证所有字段都被初始化
            assertThat(terminalCommand.getData()).isEqualTo(commandList);
            assertThat(terminalCommand.getLedId()).isEqualTo(101);
            assertThat(terminalCommand.getData().get(0).getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("应该支持构造器与Builder的混合使用")
        void should_support_constructor_and_builder_mixing() {
            // Given - 准备一个通过构造器创建的对象
            WebsocketTerminalCommand original =
                new WebsocketTerminalCommand(null, 100);

            // When - 通过Builder创建新对象
            WebsocketTerminalCommand modified = WebsocketTerminalCommand.builder()
                .data(Arrays.asList())
                .ledId(200)
                .build();

            // Then - 验证两个对象都有正确的值
            assertThat(original.getLedId()).isEqualTo(100);
            assertThat(modified.getLedId()).isEqualTo(200);
            assertThat(original).isNotEqualTo(modified);
        }

        @Test
        @DisplayName("WebsocketCommand应该支持完整的构造器初始化")
        void should_initialize_websocket_command_completely() {
            // Given & When - 通过构造器创建WebsocketCommand
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("raw_content");
            WebsocketTerminalCommand.WebsocketCommand command =
                new WebsocketTerminalCommand.WebsocketCommand(5, 500, "http://test.com", 100, content);

            // Then - 验证所有字段
            assertThat(command.getId()).isEqualTo(5);
            assertThat(command.getPost()).isEqualTo(500);
            assertThat(command.getAuthorUrl()).isEqualTo("http://test.com");
            assertThat(command.getKarma()).isEqualTo(100);
            assertThat(command.getContent().getRaw()).isEqualTo("raw_content");
        }

        @Test
        @DisplayName("应该支持WebsocketCommand的null内容初始化")
        void should_support_websocket_command_with_null_content() {
            // Given & When - 通过构造器创建WebsocketCommand，content为null
            WebsocketTerminalCommand.WebsocketCommand command =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, null);

            // Then - 验证对象能正确处理null content
            assertThat(command.getContent()).isNull();
            assertThat(command.getId()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("异常处理和边界值测试")
    class ExceptionHandlingAndBoundaryTests {

        @Test
        @DisplayName("应该处理null的data列表")
        void should_handle_null_data_list() {
            // Given & When - 创建包含null data的对象
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(null, 101);

            // Then - 验证能正确处理null
            assertThat(terminalCommand.getData()).isNull();
            assertThat(terminalCommand.getLedId()).isEqualTo(101);
        }

        @Test
        @DisplayName("应该处理null的ledId")
        void should_handle_null_ledId() {
            // Given & When - 创建包含null ledId的对象
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(Arrays.asList(), null);

            // Then - 验证能正确处理null ledId
            assertThat(terminalCommand.getLedId()).isNull();
            assertThat(terminalCommand.getData()).isEmpty();
        }

        @Test
        @DisplayName("应该处理zero值的ledId")
        void should_handle_zero_ledId() {
            // Given & When - 创建包含zero ledId的对象
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(null, 0);

            // Then - 验证zero值被正确处理
            assertThat(terminalCommand.getLedId()).isEqualTo(0);
        }

        @Test
        @DisplayName("应该处理负数ledId")
        void should_handle_negative_ledId() {
            // Given & When - 创建包含负数ledId的对象
            WebsocketTerminalCommand terminalCommand =
                new WebsocketTerminalCommand(null, -1);

            // Then - 验证负数被正确处理
            assertThat(terminalCommand.getLedId()).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("WebsocketCommand equals和hashCode分支覆盖测试")
    class WebsocketCommandEqualsHashCodeTests {

        @Test
        @DisplayName("应该正确比较相同的WebsocketCommand对象")
        void should_equal_same_websocket_commands() {
            // Given - 创建两个相同的 WebsocketCommand 对象
            WebsocketTerminalCommand.WebsocketContent content1 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd1 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content1);

            WebsocketTerminalCommand.WebsocketContent content2 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd2 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content2);

            // Then - 应该相等
            assertThat(cmd1).isEqualTo(cmd2);
            assertThat(cmd1.hashCode()).isEqualTo(cmd2.hashCode());
        }

        @Test
        @DisplayName("应该正确处理id字段不相等的情况")
        void should_not_equal_when_id_different() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd1 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);
            WebsocketTerminalCommand.WebsocketCommand cmd2 =
                new WebsocketTerminalCommand.WebsocketCommand(2, 100, "http://example.com", 50, content);

            // Then
            assertThat(cmd1).isNotEqualTo(cmd2);
        }

        @Test
        @DisplayName("应该正确处理post字段不相等的情况")
        void should_not_equal_when_post_different() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd1 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);
            WebsocketTerminalCommand.WebsocketCommand cmd2 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 200, "http://example.com", 50, content);

            // Then
            assertThat(cmd1).isNotEqualTo(cmd2);
        }

        @Test
        @DisplayName("应该正确处理authorUrl字段不相等的情况")
        void should_not_equal_when_author_url_different() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd1 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);
            WebsocketTerminalCommand.WebsocketCommand cmd2 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://other.com", 50, content);

            // Then
            assertThat(cmd1).isNotEqualTo(cmd2);
        }

        @Test
        @DisplayName("应该正确处理karma字段不相等的情况")
        void should_not_equal_when_karma_different() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd1 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);
            WebsocketTerminalCommand.WebsocketCommand cmd2 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 100, content);

            // Then
            assertThat(cmd1).isNotEqualTo(cmd2);
        }

        @Test
        @DisplayName("应该正确处理content字段不相等的情况")
        void should_not_equal_when_content_different() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content1 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketContent content2 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"stop\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd1 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content1);
            WebsocketTerminalCommand.WebsocketCommand cmd2 =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content2);

            // Then
            assertThat(cmd1).isNotEqualTo(cmd2);
        }

        @Test
        @DisplayName("应该正确处理与null的比较")
        void should_not_equal_null() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);

            // Then
            assertThat(cmd).isNotEqualTo(null);
        }

        @Test
        @DisplayName("应该正确处理与不同类型对象的比较")
        void should_not_equal_different_type() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);

            // Then
            assertThat(cmd).isNotEqualTo("not a WebsocketCommand");
        }

        @Test
        @DisplayName("应该在equals中正确处理null字段")
        void should_handle_null_fields_in_equals() {
            // Given
            WebsocketTerminalCommand.WebsocketCommand cmd1 =
                new WebsocketTerminalCommand.WebsocketCommand(null, null, null, null, null);
            WebsocketTerminalCommand.WebsocketCommand cmd2 =
                new WebsocketTerminalCommand.WebsocketCommand(null, null, null, null, null);

            // Then
            assertThat(cmd1).isEqualTo(cmd2);
        }

        @Test
        @DisplayName("应该在hashCode中处理null字段")
        void should_handle_null_fields_in_hash_code() {
            // Given
            WebsocketTerminalCommand.WebsocketCommand cmd1 =
                new WebsocketTerminalCommand.WebsocketCommand(null, null, null, null, null);
            WebsocketTerminalCommand.WebsocketCommand cmd2 =
                new WebsocketTerminalCommand.WebsocketCommand(null, null, null, null, null);

            // Then
            assertThat(cmd1.hashCode()).isEqualTo(cmd2.hashCode());
        }

        @Test
        @DisplayName("应该正确转换为字符串")
        void should_convert_to_string() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);

            // When & Then - 字符串表示应该包含类名
            assertThat(cmd.toString()).contains("WebsocketCommand");
        }

        @Test
        @DisplayName("应该与自己相等")
        void should_equal_itself() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketCommand cmd =
                new WebsocketTerminalCommand.WebsocketCommand(1, 100, "http://example.com", 50, content);

            // Then
            assertThat(cmd).isEqualTo(cmd);
        }
    }

    @Nested
    @DisplayName("WebsocketContent equals和hashCode分支覆盖测试")
    class WebsocketContentEqualsHashCodeTests {

        @Test
        @DisplayName("应该正确比较相同的WebsocketContent对象")
        void should_equal_same_websocket_contents() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content1 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketContent content2 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");

            // Then
            assertThat(content1).isEqualTo(content2);
            assertThat(content1.hashCode()).isEqualTo(content2.hashCode());
        }

        @Test
        @DisplayName("应该正确处理raw字段不相等的情况")
        void should_not_equal_when_raw_different() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content1 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");
            WebsocketTerminalCommand.WebsocketContent content2 =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"stop\"}");

            // Then
            assertThat(content1).isNotEqualTo(content2);
        }

        @Test
        @DisplayName("应该正确处理与null的比较")
        void should_not_equal_null() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");

            // Then
            assertThat(content).isNotEqualTo(null);
        }

        @Test
        @DisplayName("应该正确处理与不同类型对象的比较")
        void should_not_equal_different_type() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");

            // Then
            assertThat(content).isNotEqualTo("not a WebsocketContent");
        }

        @Test
        @DisplayName("应该在equals中处理null raw字段")
        void should_handle_null_raw_in_equals() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content1 =
                new WebsocketTerminalCommand.WebsocketContent(null);
            WebsocketTerminalCommand.WebsocketContent content2 =
                new WebsocketTerminalCommand.WebsocketContent(null);

            // Then
            assertThat(content1).isEqualTo(content2);
        }

        @Test
        @DisplayName("应该在hashCode中处理null raw字段")
        void should_handle_null_raw_in_hash_code() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content1 =
                new WebsocketTerminalCommand.WebsocketContent(null);
            WebsocketTerminalCommand.WebsocketContent content2 =
                new WebsocketTerminalCommand.WebsocketContent(null);

            // Then
            assertThat(content1.hashCode()).isEqualTo(content2.hashCode());
        }

        @Test
        @DisplayName("应该正确转换为字符串")
        void should_convert_to_string() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");

            // When & Then
            assertThat(content.toString()).contains("WebsocketContent");
        }

        @Test
        @DisplayName("应该与自己相等")
        void should_equal_itself() {
            // Given
            WebsocketTerminalCommand.WebsocketContent content =
                new WebsocketTerminalCommand.WebsocketContent("{\"action\":\"play\"}");

            // Then
            assertThat(content).isEqualTo(content);
        }
    }
}
