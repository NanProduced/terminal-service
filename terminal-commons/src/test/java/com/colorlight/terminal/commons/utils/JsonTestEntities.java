package com.colorlight.terminal.commons.utils;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * JsonUtils测试用的实体类集合
 */
public class JsonTestEntities {

    /**
     * 简单测试实体 - 基本属性测试
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleTestEntity {
        private String name;
        private Integer age;
        private Boolean active;
        private Double score;
    }

    /**
     * 复杂嵌套测试实体
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplexTestEntity {
        private String id;
        private SimpleTestEntity user;
        private Address address;
        private List<String> tags;
        private Map<String, Object> metadata;
    }

    /**
     * 地址实体 - 用于嵌套测试
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street;
        private String city;
        private String zipCode;
        private Coordinates coordinates;
    }

    /**
     * 坐标实体 - 深层嵌套测试
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinates {
        private Double latitude;
        private Double longitude;
    }

    /**
     * 集合测试实体
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionTestEntity {
        private List<SimpleTestEntity> users;
        private Map<String, Integer> scores;
        private String[] tags;
        private List<List<String>> matrix;
    }

    /**
     * 时间测试实体 - 测试Java 8时间API支持
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeTestEntity {
        private String name;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * 用于测试JSON合并的目标实体
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MergeTargetEntity {
        private String name;
        private Integer age;
        private String email;
        private Boolean active;
        
        // 用于测试部分更新
        public MergeTargetEntity(String name, Integer age) {
            this.name = name;
            this.age = age;
            this.email = "default@example.com";
            this.active = false;
        }
    }

    /**
     * 包含特殊字符的测试实体
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecialCharTestEntity {
        private String normalText;
        private String textWithQuotes;
        private String textWithNewlines;
        private String textWithUnicode;
        private String nullValue;
    }
}