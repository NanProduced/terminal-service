package com.colorlight.terminal.commons.utils;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 全局通用的 Jackson JSON 处理器工具类。
 *
 * <p><b>核心设计:</b></p>
 * <ul>
 * <li><b>单例与线程安全:</b> 内部维护一个静态、线程安全的 {@link ObjectMapper} 单例。</li>
 * <li><b>预配置:</b> 预配置了常用的序列化与反序列化特性，如：
 * <ul>
 * <li>支持 Java 8 的日期时间类型 ({@code java.time.*}) (如果相关依赖存在)。</li>
 * <li>反序列化时忽略 JSON 中存在但 Java 对象中不存在的属性。</li>
 * <li>序列化时忽略值为 null 的属性，以生成更紧凑的 JSON。</li>
 * <li>提供更宽松的 JSON 格式支持（如允许单引号、无引号的字段名）。</li>
 * </ul>
 * </li>
 * <li><b>统一异常处理:</b> 将 Jackson 的受检异常 (如 {@link JsonProcessingException}) 统一封装为自定义的运行时异常
 * ({@link TechnicalException})，简化了调用方的代码。</li>
 * </ul>
 *
 * <p><b>基本用法:</b></p>
 * <pre>{@code
 * // 对象转 JSON
 * String json = JsonUtils.toJson(myObject);
 *
 * // JSON 转简单对象
 * MyObject obj = JsonUtils.fromJson(json, MyObject.class);
 *
 * // JSON 转复杂泛型对象 (如 List<MyObject>)
 * List<MyObject> list = JsonUtils.fromJson(json, new TypeReference<List<MyObject>>() {});
 * }</pre>
 *
 * @author Nan
 */
public class JsonUtils {

    private JsonUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * 全局共享的 ObjectMapper 实例，线程安全。
     */
    private static final ObjectMapper OBJECT_MAPPER;
    /**
     * 用于JSON格式化输出的ObjectWriter实例
     */
    private static final ObjectWriter  PRETTY_PRINT_WRITER;

    static {
        List<Module> modules = new ArrayList<>(1);
        try {
            Class.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
            modules.add(new JavaTimeModule());
        } catch (ClassNotFoundException ignore) {
        }

        OBJECT_MAPPER = new ObjectMapper()
                // 时区偏移
                .registerModules(modules)
                // 反序列化时有未知属性不报错
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 序列化属性不对应不报错
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // 序列化时禁止时间Date按时间戳格式序列化
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 允许Json的key没有双引号
                .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                // 允许Json有单引号
                .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
                // 忽略字段值为null的字段（为null的字段不参加序列化）
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        PRETTY_PRINT_WRITER = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
    }

    /**
     * 获取本工具类内部配置好的默认 {@link ObjectMapper} 实例。
     * <p>
     * 暴露此方法允许在其他需要自定义序列化/反序列化逻辑的地方复用这个已配置好的实例，
     * 避免重复创建和配置，保持应用内JSON处理行为的一致性。
     *
     * @return 全局共享的 ObjectMapper 实例。
     */
    public static ObjectMapper getDefaultObjectMapper() {
        return OBJECT_MAPPER;
    }

    public static String toJson(Object obj) {
        if (Objects.isNull(obj)) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, e);
        }
    }

    /**
     * 将对象序列化成Json
     * @param objectMapper 序列化器
     * @param obj 待序列化对象
     * @param defaultJson 序列化出错时的默认返回值，为null则在序列化出错时抛出异常
     * @return Json字符串
     */
    public static String toJson(ObjectMapper objectMapper, Object obj, String defaultJson) {
        if (Objects.isNull(obj)) {
            return Objects.requireNonNull(defaultJson, "JsonUtils 待序列化的对象为null");
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            if (StringUtils.isBlank(defaultJson)) {
                throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, e);
            }
            return defaultJson;
        }
    }

    /**
     * 将Json字符串反序列化成Java对象
     * @param json Json字符串
     * @param typeReference 对象类型
     * @param <T> 对象泛型
     * @return Java对象
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        Objects.requireNonNull(json, "JsonUtils 待反序列化的字符串为null");
        Objects.requireNonNull(typeReference, "JsonUtils 待反序列化的类型为null");
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, e);
        }
    }

    /**
     * 将Json字符串反序列化成Java对象
     * @param json Json字符串
     * @param clazz 对象类型
     * @param <T> 对象泛型
     * @return Java对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        Objects.requireNonNull(json, "JsonUtils 待反序列化的字符串为null");
        Objects.requireNonNull(clazz, "JsonUtils 待反序列化的类型为null");
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, e);
        }
    }

    /**
     * 将Json字符串解析成JsonNode
     * 类似FastJson的JSON.parseObject 和 JSON.parseArray 功能
     * @param json Json字符串
     * @return JsonNode
     */
    public static JsonNode fromJson(String json) {
        Objects.requireNonNull(json, "JsonUtils 待解析的字符串为null");
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, e);
        }
    }

    /**
     * 将一个Java对象转换为另一个类型的Java对象。
     * 底层利用了Jackson的序列化和反序列化机制，非常适合在POJO、Map、JsonNode之间转换。
     *
     * @param fromValue     源对象，不能为null。
     * @param toValueType   目标对象的Class类型，不能为null。
     * @param <T>           目标对象的泛型。
     * @return 转换后的目标对象。
     * @throws TechnicalException 如果转换失败。
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        Objects.requireNonNull(fromValue, "JsonUtils: The source object for conversion is null");
        Objects.requireNonNull(toValueType, "JsonUtils: The target class for conversion is null");
        try {
            return OBJECT_MAPPER.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, e);
        }
    }

    /**
     * 将一个Java对象转换为另一个复杂的Java泛型对象。
     *
     * <p><b>用法示例:</b></p>
     * <pre>{@code
     * Map<String, Object> map = ...;
     * List<User> userList = JsonUtils.convertValue(map.get("users"), new TypeReference<List<User>>() {});
     * }</pre>
     *
     * @param fromValue     源对象，不能为null。
     * @param toValueTypeRef 包含泛型信息的类型引用，不能为null。
     * @param <T>            目标对象的泛型。
     * @return 转换后的目标对象。
     * @throws TechnicalException 如果转换失败。
     */
    public static <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef) {
        Objects.requireNonNull(fromValue, "JsonUtils: The source object for conversion is null");
        Objects.requireNonNull(toValueTypeRef, "JsonUtils: The target typeReference for conversion is null");
        try {
            return OBJECT_MAPPER.convertValue(fromValue, toValueTypeRef);
        } catch (IllegalArgumentException e) {
            throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, e);
        }
    }

    /**
     * 将 JSON 片段合并（更新）到已有对象中。
     * readerForUpdating 方法会把 json 中存在的字段更新到 target 对象，忽略 json 中没有的字段。
     *
     * @param json   部分或完整的 JSON 字符串
     * @param target 需要更新的目标对象
     * @param <T>    对象类型
     * @return 被更新后的对象（同 target 引用）
     * @throws IOException 解析或合并失败时抛出
     */
    public static <T> T mergeInto(String json, T target) {
        try {
            return OBJECT_MAPPER.readerForUpdating(target)
                    .readValue(json);
        } catch (IOException e) {
            throw new TechnicalException(TechErrorCode.JSON_MERGE_EXCEPTION, e);
        }
    }

    /**
     * 将Java对象序列化为格式化的JSON字符串（美化输出）。
     * 主要用于调试和日志记录。
     *
     * @param obj 要序列化的对象。
     * @return 如果对象为null，则返回空字符串；否则返回其格式化的JSON表示形式。
     * @throws TechnicalException 如果序列化过程中发生错误。
     */
    public static String toJsonPretty(Object obj) {
        if (Objects.isNull(obj)) {
            return "";
        }
        try {
            return PRETTY_PRINT_WRITER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, e);
        }
    }



}

