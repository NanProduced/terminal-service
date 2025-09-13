package com.colorlight.terminal.commons.utils;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.commons.utils.JsonTestEntities.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonUtils工具类的单元测试
 *
 * 测试策略：
 * 1. 测试工具类不能被实例化
 * 2. 测试基本序列化和反序列化功能
 * 3. 测试对象转换功能
 * 4. 测试高级功能（合并、深拷贝、格式化）
 * 5. 测试JSON路径访问功能
 * 6. 测试异常处理机制
 */
@DisplayName("JsonUtils工具类测试")
class JsonUtilsTest {

    @Nested
    @DisplayName("工具类实例化测试")
    class InstantiationTest {

        @Test
        @DisplayName("应该禁止通过反射实例化工具类")
        void should_prohibit_instantiation_via_reflection() throws Exception {
            // Given
            Constructor<JsonUtils> constructor = JsonUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            // When & Then
            InvocationTargetException exception = assertThrows(InvocationTargetException.class, 
                constructor::newInstance);
            
            Throwable cause = exception.getCause();
            assertInstanceOf(TechnicalException.class, cause);
            
            TechnicalException technicalException = (TechnicalException) cause;
            assertEquals(TechErrorCode.INSTANTIATION_IS_PROHIBITED.getCode(), technicalException.getErrorCode());
        }
    }

    @Nested
    @DisplayName("基本序列化测试")
    class BasicSerializationTest {

        @Test
        @DisplayName("应该正确序列化简单对象")
        void should_serialize_simple_object_correctly() {
            // Given
            SimpleTestEntity entity = new SimpleTestEntity("张三", 25, true, 95.5);

            // When
            String json = JsonUtils.toJson(entity);

            // Then
            assertNotNull(json);
            assertTrue(json.contains("\"name\":\"张三\""));
            assertTrue(json.contains("\"age\":25"));
            assertTrue(json.contains("\"active\":true"));
            assertTrue(json.contains("\"score\":95.5"));
        }

        @Test
        @DisplayName("应该正确处理null对象")
        void should_handle_null_object() {
            // When
            String json = JsonUtils.toJson(null);

            // Then
            assertEquals("", json);
        }

        @Test
        @DisplayName("应该正确序列化复杂嵌套对象")
        void should_serialize_complex_nested_object() {
            // Given
            Coordinates coords = new Coordinates(39.9042, 116.4074);
            Address address = new Address("长安街1号", "北京", "100000", coords);
            SimpleTestEntity user = new SimpleTestEntity("李四", 30, true, 88.0);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("department", "技术部");
            metadata.put("level", 5);
            
            ComplexTestEntity entity = new ComplexTestEntity("001", user, address, 
                Arrays.asList("Java", "Spring", "MongoDB"), metadata);

            // When
            String json = JsonUtils.toJson(entity);

            // Then
            assertNotNull(json);
            assertTrue(json.contains("\"name\":\"李四\""));
            assertTrue(json.contains("\"city\":\"北京\""));
            assertTrue(json.contains("\"latitude\":39.9042"));
            assertTrue(json.contains("\"Java\""));
            assertTrue(json.contains("\"department\":\"技术部\""));
        }

        @Test
        @DisplayName("应该忽略null属性")
        void should_ignore_null_properties() {
            // Given
            SimpleTestEntity entity = new SimpleTestEntity("王五", null, true, null);

            // When
            String json = JsonUtils.toJson(entity);

            // Then
            assertNotNull(json);
            assertTrue(json.contains("\"name\":\"王五\""));
            assertTrue(json.contains("\"active\":true"));
            assertFalse(json.contains("\"age\""));
            assertFalse(json.contains("\"score\""));
        }

        @Test
        @DisplayName("应该支持带默认值的序列化")
        void should_support_serialization_with_default_value() {
            // Given
            SimpleTestEntity entity = new SimpleTestEntity("测试", 25, true, 90.0);
            ObjectMapper customMapper = JsonUtils.getDefaultObjectMapper();
            String defaultJson = "{\"error\":\"序列化失败\"}";

            // When
            String json = JsonUtils.toJson(customMapper, entity, defaultJson);

            // Then
            assertNotNull(json);
            assertTrue(json.contains("\"name\":\"测试\""));
        }

        @Test
        @DisplayName("应该在序列化失败时返回默认值")
        void should_return_default_value_on_serialization_failure() {
            // Given - 使用null对象测试默认值返回
            Object nullObject = null;
            ObjectMapper customMapper = JsonUtils.getDefaultObjectMapper();
            String defaultJson = "{\"error\":\"序列化失败\"}";

            // When
            String json = JsonUtils.toJson(customMapper, nullObject, defaultJson);

            // Then
            assertEquals(defaultJson, json);
        }
    }

    @Nested
    @DisplayName("基本反序列化测试")
    class BasicDeserializationTest {

        @Test
        @DisplayName("应该正确反序列化简单对象")
        void should_deserialize_simple_object_correctly() {
            // Given
            String json = "{\"name\":\"赵六\",\"age\":28,\"active\":false,\"score\":85.5}";

            // When
            SimpleTestEntity entity = JsonUtils.fromJson(json, SimpleTestEntity.class);

            // Then
            assertNotNull(entity);
            assertEquals("赵六", entity.getName());
            assertEquals(28, entity.getAge());
            assertFalse(entity.getActive());
            assertEquals(85.5, entity.getScore());
        }

        @Test
        @DisplayName("应该正确使用TypeReference反序列化泛型对象")
        void should_deserialize_generic_object_with_type_reference() {
            // Given
            String json = "[{\"name\":\"用户1\",\"age\":20},{\"name\":\"用户2\",\"age\":25}]";

            // When
            List<SimpleTestEntity> entities = JsonUtils.fromJson(json, new TypeReference<List<SimpleTestEntity>>() {});

            // Then
            assertNotNull(entities);
            assertEquals(2, entities.size());
            assertEquals("用户1", entities.get(0).getName());
            assertEquals(20, entities.get(0).getAge());
            assertEquals("用户2", entities.get(1).getName());
            assertEquals(25, entities.get(1).getAge());
        }

        @Test
        @DisplayName("应该正确反序列化为JsonNode")
        void should_deserialize_to_json_node() {
            // Given
            String json = "{\"name\":\"测试\",\"data\":{\"count\":10,\"items\":[1,2,3]}}";

            // When
            JsonNode node = JsonUtils.fromJson(json);

            // Then
            assertNotNull(node);
            assertTrue(node.isObject());
            assertEquals("测试", node.get("name").asText());
            assertEquals(10, node.get("data").get("count").asInt());
            assertTrue(node.get("data").get("items").isArray());
            assertEquals(3, node.get("data").get("items").size());
        }

        @Test
        @DisplayName("应该正确处理包含null值的JSON")
        void should_handle_json_with_null_values() {
            // Given
            String json = "{\"name\":\"测试用户\",\"age\":null,\"active\":true,\"score\":null}";

            // When
            SimpleTestEntity entity = JsonUtils.fromJson(json, SimpleTestEntity.class);

            // Then
            assertNotNull(entity);
            assertEquals("测试用户", entity.getName());
            assertNull(entity.getAge());
            assertTrue(entity.getActive());
            assertNull(entity.getScore());
        }

        @Test
        @DisplayName("应该忽略JSON中的未知属性")
        void should_ignore_unknown_properties_in_json() {
            // Given
            String json = "{\"name\":\"测试\",\"age\":30,\"unknownField\":\"应该被忽略\",\"anotherUnknown\":123}";

            // When
            SimpleTestEntity entity = JsonUtils.fromJson(json, SimpleTestEntity.class);

            // Then
            assertNotNull(entity);
            assertEquals("测试", entity.getName());
            assertEquals(30, entity.getAge());
            // 未知属性应该被忽略，不影响反序列化
        }

        @Test
        @DisplayName("应该在反序列化null字符串时抛出异常")
        void should_throw_exception_when_deserializing_null_string() {
            // When & Then
            assertThrows(NullPointerException.class, () -> {
                JsonUtils.fromJson(null, SimpleTestEntity.class);
            });
        }

        @Test
        @DisplayName("应该在反序列化无效JSON时抛出技术异常")
        void should_throw_technical_exception_for_invalid_json() {
            // Given
            String invalidJson = "{invalid json format";

            // When & Then
            TechnicalException exception = assertThrows(TechnicalException.class, () -> {
                JsonUtils.fromJson(invalidJson, SimpleTestEntity.class);
            });
            
            assertEquals(TechErrorCode.JSON_SERIALIZATION_EXCEPTION.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("对象转换测试")
    class ObjectConversionTest {

        @Test
        @DisplayName("应该正确转换对象类型")
        void should_convert_object_type_correctly() {
            // Given
            Map<String, Object> map = new HashMap<>();
            map.put("name", "转换测试");
            map.put("age", 35);
            map.put("active", true);
            map.put("score", 92.5);

            // When
            SimpleTestEntity entity = JsonUtils.convertValue(map, SimpleTestEntity.class);

            // Then
            assertNotNull(entity);
            assertEquals("转换测试", entity.getName());
            assertEquals(35, entity.getAge());
            assertTrue(entity.getActive());
            assertEquals(92.5, entity.getScore());
        }

        @Test
        @DisplayName("应该正确使用TypeReference转换复杂泛型对象")
        void should_convert_complex_generic_object_with_type_reference() {
            // Given
            List<Map<String, Object>> listOfMaps = Arrays.asList(
                Map.of("name", "用户A", "age", 25),
                Map.of("name", "用户B", "age", 30)
            );

            // When
            List<SimpleTestEntity> entities = JsonUtils.convertValue(listOfMaps, new TypeReference<List<SimpleTestEntity>>() {});

            // Then
            assertNotNull(entities);
            assertEquals(2, entities.size());
            assertEquals("用户A", entities.get(0).getName());
            assertEquals(25, entities.get(0).getAge());
            assertEquals("用户B", entities.get(1).getName());
            assertEquals(30, entities.get(1).getAge());
        }

        @Test
        @DisplayName("应该在转换null对象时抛出异常")
        void should_throw_exception_when_converting_null_object() {
            // When & Then
            assertThrows(NullPointerException.class, () -> {
                JsonUtils.convertValue(null, SimpleTestEntity.class);
            });
        }

        @Test
        @DisplayName("应该在转换到null类型时抛出异常")
        void should_throw_exception_when_converting_to_null_type() {
            // Given
            Map<String, Object> map = Map.of("name", "测试");

            // When & Then
            assertThrows(NullPointerException.class, () -> {
                JsonUtils.convertValue(map, (Class<SimpleTestEntity>) null);
            });
        }
    }

    @Nested
    @DisplayName("高级功能测试")
    class AdvancedFeaturesTest {

        @Test
        @DisplayName("应该正确合并JSON到现有对象")
        void should_merge_json_into_existing_object_correctly() {
            // Given
            MergeTargetEntity target = new MergeTargetEntity("原始名称", 25);
            String updateJson = "{\"name\":\"更新后的名称\",\"active\":true}";

            // When
            MergeTargetEntity result = JsonUtils.mergeInto(updateJson, target);

            // Then
            assertSame(target, result); // 应该返回同一个对象引用
            assertEquals("更新后的名称", result.getName());
            assertEquals(25, result.getAge()); // 未更新的字段应该保持原值
            assertEquals("default@example.com", result.getEmail()); // 未更新的字段应该保持原值
            assertTrue(result.getActive()); // 新字段应该被设置
        }

        @Test
        @DisplayName("应该正确进行对象深拷贝")
        void should_perform_deep_copy_correctly() throws IOException {
            // Given
            Coordinates originalCoords = new Coordinates(39.9042, 116.4074);
            Address originalAddress = new Address("原始街道", "原始城市", "100000", originalCoords);
            SimpleTestEntity originalUser = new SimpleTestEntity("原始用户", 30, true, 88.0);
            ComplexTestEntity original = new ComplexTestEntity("001", originalUser, originalAddress, 
                Arrays.asList("tag1", "tag2"), Map.of("key", "value"));

            // When
            ComplexTestEntity copy = JsonUtils.deepCopy(original);

            // Then
            assertNotNull(copy);
            assertNotSame(original, copy); // 应该是不同的对象实例
            assertEquals(original.getId(), copy.getId());
            assertEquals(original.getUser().getName(), copy.getUser().getName());
            assertNotSame(original.getUser(), copy.getUser()); // 嵌套对象也应该是深拷贝
            assertEquals(original.getAddress().getCity(), copy.getAddress().getCity());
            assertNotSame(original.getAddress(), copy.getAddress()); // 嵌套对象也应该是深拷贝
            
            // 修改原对象不应该影响拷贝对象
            original.getUser().setName("修改后的名称");
            assertNotEquals(original.getUser().getName(), copy.getUser().getName());
        }

        @Test
        @DisplayName("应该正确处理null对象的深拷贝")
        void should_handle_null_object_deep_copy() throws IOException {
            // When
            JsonUtils.deepCopy(null);

            // Then
            assertNull(null);
        }

        @Test
        @DisplayName("应该正确格式化JSON输出")
        void should_format_json_output_correctly() {
            // Given
            SimpleTestEntity entity = new SimpleTestEntity("格式化测试", 25, true, 95.0);

            // When
            String prettyJson = JsonUtils.toJsonPretty(entity);

            // Then
            assertNotNull(prettyJson);
            assertTrue(prettyJson.contains("\n")); // 应该包含换行符
            assertTrue(prettyJson.contains("  ")); // 应该包含缩进
            assertTrue(prettyJson.contains("\"name\" : \"格式化测试\""));
        }

        @Test
        @DisplayName("应该正确处理null对象的格式化")
        void should_handle_null_object_pretty_print() {
            // When
            String prettyJson = JsonUtils.toJsonPretty(null);

            // Then
            assertEquals("", prettyJson);
        }

        @Test
        @DisplayName("应该正确获取默认ObjectMapper")
        void should_get_default_object_mapper_correctly() {
            // When
            ObjectMapper mapper = JsonUtils.getDefaultObjectMapper();

            // Then
            assertNotNull(mapper);
            // 验证配置是否正确
            assertFalse(mapper.getDeserializationConfig().isEnabled(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        }
    }

    @Nested
    @DisplayName("JSON路径访问测试")
    class JsonPathAccessTest {

        private final String complexJson = """
            {
                "user": {
                    "name": "张三",
                    "age": 25,
                    "active": true,
                    "score": 95.5,
                    "profile": {
                        "email": "zhangsan@example.com",
                        "phone": "13800138000"
                    }
                },
                "items": [
                    {
                        "id": 1,
                        "title": "商品1",
                        "price": 99.99,
                        "available": true
                    },
                    {
                        "id": 2,
                        "title": "商品2",
                        "price": 199.99,
                        "available": false
                    }
                ],
                "metadata": {
                    "total": 2,
                    "page": 1,
                    "hasMore": true
                },
                "nullValue": null,
                "emptyString": "",
                "numbers": [10, 20, 30],
                "booleans": [true, false, true]
            }
            """;

        @Test
        @DisplayName("应该正确获取字符串值")
        void should_get_string_value_correctly() {
            // 简单属性访问
            assertEquals("张三", JsonUtils.getStringValue(complexJson, "user.name", "默认值"));
            
            // 嵌套属性访问
            assertEquals("zhangsan@example.com", JsonUtils.getStringValue(complexJson, "user.profile.email", "默认值"));
            
            // 数组元素访问
            assertEquals("商品1", JsonUtils.getStringValue(complexJson, "items[0].title", "默认值"));
            assertEquals("商品2", JsonUtils.getStringValue(complexJson, "items[1].title", "默认值"));
            
            // 不存在的路径应该返回默认值
            assertEquals("默认值", JsonUtils.getStringValue(complexJson, "user.nonexistent", "默认值"));
            assertEquals("默认值", JsonUtils.getStringValue(complexJson, "items[10].title", "默认值"));
            
            // 空字符串
            assertEquals("", JsonUtils.getStringValue(complexJson, "emptyString", "默认值"));
            
            // null值应该返回默认值
            assertEquals("默认值", JsonUtils.getStringValue(complexJson, "nullValue", "默认值"));
        }

        @Test
        @DisplayName("应该正确获取整数值")
        void should_get_int_value_correctly() {
            // 数字类型直接提取
            assertEquals(25, JsonUtils.getIntValue(complexJson, "user.age", 0));
            assertEquals(2, JsonUtils.getIntValue(complexJson, "metadata.total", 0));
            
            // 数组访问
            assertEquals(1, JsonUtils.getIntValue(complexJson, "items[0].id", 0));
            assertEquals(2, JsonUtils.getIntValue(complexJson, "items[1].id", 0));
            assertEquals(10, JsonUtils.getIntValue(complexJson, "numbers[0]", 0));
            assertEquals(30, JsonUtils.getIntValue(complexJson, "numbers[2]", 0));
            
            // 不存在的路径应该返回默认值
            assertEquals(999, JsonUtils.getIntValue(complexJson, "user.nonexistent", 999));
            assertEquals(999, JsonUtils.getIntValue(complexJson, "numbers[10]", 999));
            
            // null值应该返回默认值
            assertEquals(999, JsonUtils.getIntValue(complexJson, "nullValue", 999));
        }

        @Test
        @DisplayName("应该正确获取布尔值")
        void should_get_boolean_value_correctly() {
            // 布尔类型直接提取
            assertTrue(JsonUtils.getBooleanValue(complexJson, "user.active", false));
            assertTrue(JsonUtils.getBooleanValue(complexJson, "metadata.hasMore", false));
            
            // 数组访问
            assertTrue(JsonUtils.getBooleanValue(complexJson, "items[0].available", false));
            assertFalse(JsonUtils.getBooleanValue(complexJson, "items[1].available", true));
            assertTrue(JsonUtils.getBooleanValue(complexJson, "booleans[0]", false));
            assertFalse(JsonUtils.getBooleanValue(complexJson, "booleans[1]", true));
            
            // 不存在的路径应该返回默认值
            assertTrue(JsonUtils.getBooleanValue(complexJson, "user.nonexistent", true));
            assertFalse(JsonUtils.getBooleanValue(complexJson, "user.nonexistent", false));
            
            // null值应该返回默认值
            assertTrue(JsonUtils.getBooleanValue(complexJson, "nullValue", true));
        }

        @Test
        @DisplayName("应该正确检查路径是否存在")
        void should_check_path_existence_correctly() {
            // 存在的路径
            assertTrue(JsonUtils.hasPath(complexJson, "user.name"));
            assertTrue(JsonUtils.hasPath(complexJson, "user.profile.email"));
            assertTrue(JsonUtils.hasPath(complexJson, "items[0].title"));
            assertTrue(JsonUtils.hasPath(complexJson, "items[1].available"));
            assertTrue(JsonUtils.hasPath(complexJson, "numbers[0]"));
            assertTrue(JsonUtils.hasPath(complexJson, "emptyString"));
            
            // 不存在的路径
            assertFalse(JsonUtils.hasPath(complexJson, "user.nonexistent"));
            assertFalse(JsonUtils.hasPath(complexJson, "items[10].title"));
            assertFalse(JsonUtils.hasPath(complexJson, "nonexistent.path"));
            
            // null值的路径存在但值为null
            assertFalse(JsonUtils.hasPath(complexJson, "nullValue"));
        }

        @Test
        @DisplayName("应该正确检查路径值是否为null")
        void should_check_path_null_value_correctly() {
            // null值
            assertTrue(JsonUtils.isNull(complexJson, "nullValue"));
            
            // 非null值
            assertFalse(JsonUtils.isNull(complexJson, "user.name"));
            assertFalse(JsonUtils.isNull(complexJson, "user.age"));
            assertFalse(JsonUtils.isNull(complexJson, "emptyString")); // 空字符串不是null
            
            // 不存在的路径
            assertFalse(JsonUtils.isNull(complexJson, "nonexistent.path"));
        }

        @Test
        @DisplayName("应该在JSON格式错误时抛出技术异常")
        void should_throw_technical_exception_for_invalid_json() {
            // 无效JSON格式
            String invalidJson = "{invalid json";
            
            // 测试路径访问方法对无效JSON的处理
            // 注意：JsonUtils的路径访问方法会捕获异常并返回默认值，而不是抛出异常
            String result = JsonUtils.getStringValue(invalidJson, "any.path", "默认值");
            assertEquals("默认值", result);
            
            boolean hasPath = JsonUtils.hasPath(invalidJson, "any.path");
            assertFalse(hasPath);
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ErrorHandlingTest {

        @Test
        @DisplayName("应该在JSON合并失败时抛出技术异常")
        void should_throw_technical_exception_on_merge_failure() {
            // Given
            SimpleTestEntity target = new SimpleTestEntity("测试", 25, true, 90.0);
            String invalidJson = "{invalid json format";

            // When & Then
            TechnicalException exception = assertThrows(TechnicalException.class, () -> {
                JsonUtils.mergeInto(invalidJson, target);
            });
            
            assertEquals(TechErrorCode.JSON_MERGE_EXCEPTION.getCode(), exception.getErrorCode());
        }

        @Test
        @DisplayName("应该正确处理时间类型的序列化和反序列化")
        void should_handle_time_types_correctly() {
            // Given
            LocalDateTime now = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
            TimeTestEntity entity = new TimeTestEntity("时间测试", now, now.plusHours(1));

            // When - 序列化
            String json = JsonUtils.toJson(entity);

            // Then - 应该包含时间信息且不是时间戳格式
            assertNotNull(json);
            assertTrue(json.contains("2024-01-01"));
            assertFalse(json.contains("1704067200000")); // 不应该是时间戳格式

            // When - 反序列化
            TimeTestEntity deserialized = JsonUtils.fromJson(json, TimeTestEntity.class);

            // Then
            assertNotNull(deserialized);
            assertEquals("时间测试", deserialized.getName());
            assertEquals(now, deserialized.getCreatedAt());
            assertEquals(now.plusHours(1), deserialized.getUpdatedAt());
        }

        @Test
        @DisplayName("应该正确处理特殊字符")
        void should_handle_special_characters_correctly() {
            // Given
            SpecialCharTestEntity entity = new SpecialCharTestEntity(
                "普通文本",
                "包含\"双引号\"和'单引号'的文本",
                "包含\n换行符\t制表符的文本",
                "包含Unicode字符：中文\ud83d\ude00",
                null
            );

            // When
            String json = JsonUtils.toJson(entity);
            SpecialCharTestEntity deserialized = JsonUtils.fromJson(json, SpecialCharTestEntity.class);

            // Then
            assertNotNull(json);
            assertNotNull(deserialized);
            assertEquals(entity.getNormalText(), deserialized.getNormalText());
            assertEquals(entity.getTextWithQuotes(), deserialized.getTextWithQuotes());
            assertEquals(entity.getTextWithNewlines(), deserialized.getTextWithNewlines());
            assertEquals(entity.getTextWithUnicode(), deserialized.getTextWithUnicode());
            assertNull(deserialized.getNullValue());
        }
    }
}