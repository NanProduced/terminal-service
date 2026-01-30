package com.colorlight.terminal.commons.utils;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        throw new TechnicalException(TechErrorCode.INSTANTIATION_IS_PROHIBITED);
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

    /**
     * 使用JSON序列化和反序列化实现对象的深拷贝。
     *
     * @param <T>   对象的类型
     * @param source 要进行深拷贝的源对象
     * @return      一个全新的、与源对象完全独立的深拷贝副本
     * @throws IOException 如果序列化或反序列化过程中发生错误
     */
    public static <T> T deepCopy(T source) throws IOException {
        if (source == null) {
            return null;
        }
        // 将源对象序列化为JSON字符串
        String json = OBJECT_MAPPER.writeValueAsString(source);
        // 从JSON字符串反序列化回一个新的对象实例
        return (T) OBJECT_MAPPER.readValue(json, source.getClass());
    }

    // ========================================
    // JSON 路径访问 API (新增功能)
    // ========================================

    /**
     * 根据路径从JsonNode中获取子节点。
     * 支持点号与数组索引访问（格式与字符串版本一致）。
     *
     * @param rootNode 根节点
     * @param path 路径表达式
     * @return 目标节点，不存在时返回MissingNode
     */
    public static JsonNode getNodeByPath(JsonNode rootNode, String path) {
        if (rootNode == null || rootNode.isMissingNode()) {
            return MissingNode.getInstance();
        }
        if (StringUtils.isBlank(path)) {
            return MissingNode.getInstance();
        }
        if (".".equals(path) || "root".equals(path)) {
            return rootNode;
        }
        JsonNode node = navigateNode(rootNode, path);
        return node == null ? MissingNode.getInstance() : node;
    }

    /**
     * 收集JsonNode中出现的字段路径。
     * 规则与Mongo增量更新一致：对象节点记录为objectPaths，数组视为叶子节点。
     *
     * @param rootNode 根节点
     * @return 路径集合摘要
     */
    public static JsonPathSummary collectPaths(JsonNode rootNode) {
        Set<String> leafPaths = new LinkedHashSet<>();
        Set<String> objectPaths = new LinkedHashSet<>();
        collectPathsInternal(rootNode, "", leafPaths, objectPaths);
        return new JsonPathSummary(leafPaths, objectPaths);
    }

    /**
     * 根据路径从JSON字符串中提取字符串值。
     * 类似 {@code new JSONObject(data).get("data").toString()} 的功能。
     * 
     * <p><b>路径格式支持：</b></p>
     * <ul>
     * <li><b>简单属性：</b>{@code "name"} - 获取根对象的name属性</li>
     * <li><b>嵌套属性：</b>{@code "user.profile.name"} - 获取嵌套对象属性</li>
     * <li><b>数组访问：</b>{@code "users[0].name"} - 获取数组第一个元素的name属性</li>
     * <li><b>混合路径：</b>{@code "data.items[2].details.title"} - 复杂嵌套路径</li>
     * </ul>
     *
     * <p><b>可靠性设计：</b></p>
     * <ul>
     * <li><b>空值安全：</b>路径中任何节点为null时返回默认值而不抛异常</li>
     * <li><b>类型容错：</b>类型不匹配时返回默认值</li>
     * <li><b>边界保护：</b>数组越界时返回默认值</li>
     * </ul>
     *
     * <p><b>用法示例：</b></p>
     * <pre>{@code
     * String json = "{\"user\":{\"name\":\"张三\",\"age\":25},\"items\":[{\"title\":\"商品1\"}]}";
     * 
     * // 简单属性访问
     * String userName = JsonUtils.getStringValue(json, "user.name", "未知用户");
     * // 数组访问
     * String itemTitle = JsonUtils.getStringValue(json, "items[0].title", "无标题");
     * // 不存在的路径返回默认值
     * String missing = JsonUtils.getStringValue(json, "user.address", "地址未填写");
     * }</pre>
     *
     * @param json JSON字符串，不能为null
     * @param path JSON路径表达式，使用点号分隔嵌套属性，数组用方括号表示索引
     * @param defaultValue 当路径不存在、值为null或类型不匹配时的默认返回值
     * @return 提取的字符串值，失败时返回默认值
     * @throws TechnicalException 当JSON格式不正确或路径格式错误时抛出
     */
    public static String getStringValue(String json, String path, String defaultValue) {
        return getValue(json, path, String.class, defaultValue);
    }

    /**
     * 根据路径从JSON字符串中提取整数值。
     * 支持数字类型的自动转换，字符串类型的数字也会尝试解析。
     *
     * <p><b>用法示例：</b></p>
     * <pre>{@code
     * String json = "{\"user\":{\"age\":25,\"score\":\"95\"},\"counts\":[10,20,30]}";
     * 
     * // 数字类型直接提取
     * int age = JsonUtils.getIntValue(json, "user.age", 0);
     * // 字符串数字自动转换
     * int score = JsonUtils.getIntValue(json, "user.score", 0);
     * // 数组访问
     * int firstCount = JsonUtils.getIntValue(json, "counts[0]", 0);
     * }</pre>
     *
     * @param json JSON字符串，不能为null
     * @param path JSON路径表达式
     * @param defaultValue 当路径不存在、值为null或无法转换为整数时的默认返回值
     * @return 提取的整数值，失败时返回默认值
     * @throws TechnicalException 当JSON格式不正确或路径格式错误时抛出
     */
    public static int getIntValue(String json, String path, int defaultValue) {
        try {
            JsonNode node = navigateToPath(json, path);
            if (node == null || node.isNull()) {
                return defaultValue;
            }
            if (node.isNumber()) {
                return node.asInt();
            }
            if (node.isTextual()) {
                return Integer.parseInt(node.asText());
            }
            return defaultValue;
        } catch (NumberFormatException | TechnicalException e) {
            return defaultValue;
        }
    }

    /**
     * 根据路径从JSON字符串中提取长整数值。
     * 支持数字类型的自动转换，字符串类型的数字也会尝试解析。
     *
     * @param json JSON字符串，不能为null
     * @param path JSON路径表达式
     * @param defaultValue 当路径不存在、值为null或无法转换为长整数时的默认返回值
     * @return 提取的长整数值，失败时返回默认值
     * @throws TechnicalException 当JSON格式不正确或路径格式错误时抛出
     */
    public static long getLongValue(String json, String path, long defaultValue) {
        try {
            JsonNode node = navigateToPath(json, path);
            if (node == null || node.isNull()) {
                return defaultValue;
            }
            if (node.isNumber()) {
                return node.asLong();
            }
            if (node.isTextual()) {
                return Long.parseLong(node.asText());
            }
            return defaultValue;
        } catch (NumberFormatException | TechnicalException e) {
            return defaultValue;
        }
    }

    /**
     * 根据路径从JSON字符串中提取布尔值。
     * 支持布尔类型直接提取，字符串类型的布尔值也会尝试解析。
     *
     * <p><b>布尔值解析规则：</b></p>
     * <ul>
     * <li><b>true值：</b>布尔true、字符串"true"(不区分大小写)、数字1</li>
     * <li><b>false值：</b>布尔false、字符串"false"(不区分大小写)、数字0</li>
     * <li><b>其他值：</b>返回默认值</li>
     * </ul>
     *
     * @param json JSON字符串，不能为null
     * @param path JSON路径表达式
     * @param defaultValue 当路径不存在、值为null或无法转换为布尔值时的默认返回值
     * @return 提取的布尔值，失败时返回默认值
     * @throws TechnicalException 当JSON格式不正确或路径格式错误时抛出
     */
    public static boolean getBooleanValue(String json, String path, boolean defaultValue) {
        try {
            JsonNode node = navigateToPath(json, path);
            if (node == null || node.isNull()) {
                return defaultValue;
            }
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            if (node.isTextual()) {
                String text = node.asText().toLowerCase();
                if ("true".equals(text)) return true;
                if ("false".equals(text)) return false;
                return defaultValue;
            }
            if (node.isNumber()) {
                int value = node.asInt();
                return value == 1 || (value != 0 && defaultValue);
            }
            return defaultValue;
        } catch (TechnicalException e) {
            return defaultValue;
        }
    }

    /**
     * 根据路径从JSON字符串中提取指定类型的值。
     * 这是一个通用的类型安全访问方法，支持所有Jackson可转换的类型。
     *
     * <p><b>支持的类型包括：</b></p>
     * <ul>
     * <li><b>基本类型：</b>String, Integer, Long, Boolean, Double等</li>
     * <li><b>复杂对象：</b>自定义POJO类</li>
     * <li><b>集合类型：</b>List, Map等（需要配合TypeReference使用）</li>
     * </ul>
     *
     * <p><b>用法示例：</b></p>
     * <pre>{@code
     * String json = "{\"user\":{\"name\":\"张三\",\"age\":25},\"tags\":[\"tag1\",\"tag2\"]}";
     * 
     * // 基本类型
     * String name = JsonUtils.getValue(json, "user.name", String.class, "默认名称");
     * Integer age = JsonUtils.getValue(json, "user.age", Integer.class, 0);
     * 
     * // 复杂对象（假设有User类）
     * User user = JsonUtils.getValue(json, "user", User.class, new User());
     * }</pre>
     *
     * @param <T> 目标类型的泛型
     * @param json JSON字符串，不能为null
     * @param path JSON路径表达式
     * @param clazz 目标类型的Class对象
     * @param defaultValue 当路径不存在、值为null或类型转换失败时的默认返回值
     * @return 提取并转换后的值，失败时返回默认值
     * @throws TechnicalException 当JSON格式不正确或路径格式错误时抛出
     */
    public static <T> T getValue(String json, String path, Class<T> clazz, T defaultValue) {
        try {
            JsonNode node = navigateToPath(json, path);
            if (node == null || node.isNull()) {
                return defaultValue;
            }
            return OBJECT_MAPPER.convertValue(node, clazz);
        } catch (IllegalArgumentException | TechnicalException e) {
            return defaultValue;
        }
    }

    /**
     * 检查JSON字符串中指定路径是否存在。
     * 
     * <p><b>存在性判断规则：</b></p>
     * <ul>
     * <li><b>存在：</b>路径可达且值不为null</li>
     * <li><b>不存在：</b>路径中任何节点缺失、值为null、数组越界</li>
     * </ul>
     *
     * @param json JSON字符串，不能为null
     * @param path JSON路径表达式
     * @return 如果路径存在且值不为null则返回true，否则返回false
     * @throws TechnicalException 当JSON格式不正确或路径格式错误时抛出
     */
    public static boolean hasPath(String json, String path) {
        try {
            JsonNode node = navigateToPath(json, path);
            return node != null && !node.isNull();
        } catch (TechnicalException e) {
            return false;
        }
    }

    /**
     * 检查JSON字符串中指定路径的值是否为null。
     * 与{@link #hasPath}的区别：路径存在但值为JSON null时此方法返回true。
     *
     * @param json JSON字符串，不能为null
     * @param path JSON路径表达式
     * @return 如果路径存在且值为null则返回true，路径不存在或值不为null则返回false
     * @throws TechnicalException 当JSON格式不正确或路径格式错误时抛出
     */
    public static boolean isNull(String json, String path) {
        try {
            JsonNode node = navigateToPath(json, path);
            return node != null && node.isNull();
        } catch (TechnicalException e) {
            return false;
        }
    }

    // ========================================
    // 内部辅助方法
    // ========================================

    /**
     * 根据路径导航到JSON节点。
     * 这是路径访问功能的核心实现，负责解析路径表达式并导航到目标节点。
     *
     * <p><b>路径解析逻辑：</b></p>
     * <ul>
     * <li><b>属性访问：</b>使用点号分隔，如"user.profile.name"</li>
     * <li><b>数组访问：</b>使用方括号包围索引，如"items[0]"或"data.users[1].name"</li>
     * <li><b>混合路径：</b>支持属性和数组的任意组合</li>
     * </ul>
     *
     * <p><b>错误处理策略：</b></p>
     * <ul>
     * <li><b>路径不存在：</b>返回null而不抛异常</li>
     * <li><b>数组越界：</b>返回null而不抛异常</li>
     * <li><b>类型错误：</b>如尝试在非对象节点访问属性时返回null</li>
     * </ul>
     *
     * @param json JSON字符串
     * @param path 路径表达式
     * @return 目标节点，路径不存在时返回null
     * @throws TechnicalException 当JSON解析失败或路径格式错误时抛出
     */
    private static JsonNode navigateToPath(String json, String path) {
        Objects.requireNonNull(json, "JsonUtils: JSON字符串不能为null");
        Objects.requireNonNull(path, "JsonUtils: 路径不能为null");
        
        if (StringUtils.isBlank(path)) {
            throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, "路径不能为空");
        }

        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(json);
            if (rootNode == null) {
                return null;
            }

            // 根路径直接返回根节点
            if (".".equals(path) || "root".equals(path)) {
                return rootNode;
            }

            return navigateNode(rootNode, path);
            
        } catch (IOException e) {
            throw new TechnicalException(TechErrorCode.JSON_SERIALIZATION_EXCEPTION, "JSON解析失败", e);
        }
    }

    /**
     * 在给定节点上执行路径导航。
     * 递归解析路径表达式，支持点号分隔的属性访问和数组索引访问。
     *
     * @param currentNode 当前节点
     * @param remainingPath 剩余路径
     * @return 目标节点，失败时返回null
     */
    private static JsonNode navigateNode(JsonNode currentNode, String remainingPath) {
        if (currentNode == null || StringUtils.isBlank(remainingPath)) {
            return currentNode;
        }

        // 查找下一个分隔符的位置
        int dotIndex = remainingPath.indexOf('.');
        int bracketIndex = remainingPath.indexOf('[');

        // 决定下一个要处理的路径部分
        String nextSegment;
        String restPath;

        if (bracketIndex != -1 && (dotIndex == -1 || bracketIndex < dotIndex)) {
            // 处理数组访问，如 "items[0]" 或 "users[1].name"
            nextSegment = remainingPath.substring(0, bracketIndex);
            
            // 查找对应的右括号
            int rightBracket = remainingPath.indexOf(']', bracketIndex);
            if (rightBracket == -1) {
                return null; // 格式错误：缺少右括号
            }
            
            String indexStr = remainingPath.substring(bracketIndex + 1, rightBracket);
            try {
                int arrayIndex = Integer.parseInt(indexStr);
                
                // 先访问属性（如果有）
                JsonNode arrayNode = StringUtils.isBlank(nextSegment) ? 
                    currentNode : currentNode.get(nextSegment);
                
                if (arrayNode == null || !arrayNode.isArray() || arrayIndex < 0 || arrayIndex >= arrayNode.size()) {
                    return null;
                }
                
                JsonNode elementNode = arrayNode.get(arrayIndex);
                
                // 处理剩余路径
                int nextDot = remainingPath.indexOf('.', rightBracket);
                if (nextDot == -1) {
                    return elementNode;
                } else {
                    return navigateNode(elementNode, remainingPath.substring(nextDot + 1));
                }
                
            } catch (NumberFormatException e) {
                return null; // 数组索引不是有效数字
            }
            
        } else if (dotIndex != -1) {
            // 处理属性访问，如 "user.name"
            nextSegment = remainingPath.substring(0, dotIndex);
            restPath = remainingPath.substring(dotIndex + 1);
        } else {
            // 最后一个属性
            nextSegment = remainingPath;
            restPath = null;
        }

        // 访问属性
        if (!currentNode.isObject()) {
            return null; // 无法在非对象节点上访问属性
        }

        JsonNode nextNode = currentNode.get(nextSegment);
        
        // 继续处理剩余路径
        if (StringUtils.isBlank(restPath)) {
            return nextNode;
        } else {
            return navigateNode(nextNode, restPath);
        }
    }

    private static void collectPathsInternal(JsonNode node,
                                             String prefix,
                                             Set<String> leafPaths,
                                             Set<String> objectPaths) {
        if (node == null || node.isNull()) {
            if (!prefix.isEmpty()) {
                leafPaths.add(prefix);
            }
            return;
        }
        if (node.isObject()) {
            if (!prefix.isEmpty()) {
                objectPaths.add(prefix);
            }
            node.fields().forEachRemaining(entry -> {
                String nextPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                collectPathsInternal(entry.getValue(), nextPrefix, leafPaths, objectPaths);
            });
            return;
        }
        if (node.isArray()) {
            if (!prefix.isEmpty()) {
                leafPaths.add(prefix);
            }
            return;
        }
        if (!prefix.isEmpty()) {
            leafPaths.add(prefix);
        }
    }

    /**
     * Json路径收集结果
     */
    public static class JsonPathSummary {
        private final Set<String> leafPaths;
        private final Set<String> objectPaths;

        private JsonPathSummary(Set<String> leafPaths, Set<String> objectPaths) {
            this.leafPaths = Collections.unmodifiableSet(new LinkedHashSet<>(leafPaths));
            this.objectPaths = Collections.unmodifiableSet(new LinkedHashSet<>(objectPaths));
        }

        public Set<String> getLeafPaths() {
            return leafPaths;
        }

        public Set<String> getObjectPaths() {
            return objectPaths;
        }
    }

}

