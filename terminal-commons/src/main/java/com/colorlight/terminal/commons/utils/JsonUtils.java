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

public class JsonUtils {

    private JsonUtils() {
        throw new UnsupportedOperationException();
    }

    private static final ObjectMapper OBJECT_MAPPER;

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
     * 将 JSON 片段合并（更新）到已有对象中。
     * readerForUpdating 方法会把 json 中存在的字段更新到 target 对象，忽略 json 中没有的字段。
     *
     * @param json   部分或完整的 JSON 字符串
     * @param target 需要更新的目标对象
     * @param <T>    对象类型
     * @return 被更新后的对象（同 target 引用）
     * @throws IOException 解析或合并失败时抛出
     */
    public static <T> T mergeInto(String json, T target) throws IOException {
        return OBJECT_MAPPER.readerForUpdating(target)
                .readValue(json);
    }

    public static ObjectMapper getDefaultObjectMapper() {
        return OBJECT_MAPPER;
    }
}

