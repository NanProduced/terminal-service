package com.colorlight.terminal.commons.utils;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BeanUtils工具类的单元测试
 *
 * 测试策略：
 * 1. 测试工具类不能被实例化
 * 2. 测试copyNonNullProperties方法的正常和边界情况
 */
@DisplayName("BeanUtils工具类测试")
class BeanUtilsTest {

    @Nested
    @DisplayName("工具类实例化测试")
    class InstantiationTest {

        @Test
        @DisplayName("应该禁止通过反射实例化工具类")
        void should_prohibit_instantiation_via_reflection() throws Exception {
            // Given
            Constructor<BeanUtils> constructor = BeanUtils.class.getDeclaredConstructor();
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
    @DisplayName("复制非空属性测试")
    class CopyNonNullPropertiesTest {

        @Test
        @DisplayName("应该成功复制非空属性")
        void should_copy_non_null_properties_successfully() {
            // Given
            TestEntity source = new TestEntity();
            source.setName("张三");
            source.setAge(25);
            source.setEmail(null); // null值不应该被复制
            source.setActive(true);

            TestEntity target = new TestEntity();
            target.setEmail("old@example.com"); // 这个值应该保留

            // When
            BeanUtils.copyNonNullProperties(source, target);

            // Then
            assertEquals("张三", target.getName());
            assertEquals(25, target.getAge());
            assertEquals("old@example.com", target.getEmail()); // 应该保留原值
            assertTrue(target.getActive());
        }

        @Test
        @DisplayName("应该处理源对象所有属性都为null的情况")
        void should_handle_all_null_source_properties() {
            // Given
            TestEntity source = new TestEntity(); // 所有属性都是null

            TestEntity target = new TestEntity();
            target.setName("李四");
            target.setAge(30);
            target.setEmail("original@example.com");
            target.setActive(false);

            // When
            BeanUtils.copyNonNullProperties(source, target);

            // Then
            assertEquals("李四", target.getName()); // 应该保留原值
            assertEquals(30, target.getAge());
            assertEquals("original@example.com", target.getEmail());
            assertFalse(target.getActive());
        }

        @Test
        @DisplayName("应该处理源对象所有属性都有值的情况")
        void should_handle_all_non_null_source_properties() {
            // Given
            TestEntity source = new TestEntity();
            source.setName("王五");
            source.setAge(35);
            source.setEmail("new@example.com");
            source.setActive(true);

            TestEntity target = new TestEntity();
            target.setName("赵六"); // 这些值应该被覆盖
            target.setAge(40);
            target.setEmail("old@example.com");
            target.setActive(false);

            // When
            BeanUtils.copyNonNullProperties(source, target);

            // Then
            assertEquals("王五", target.getName()); // 应该被新值覆盖
            assertEquals(35, target.getAge());
            assertEquals("new@example.com", target.getEmail());
            assertTrue(target.getActive());
        }

        @Test
        @DisplayName("应该处理目标对象为null的情况")
        void should_handle_null_target_object() {
            // Given
            TestEntity source = new TestEntity();
            source.setName("测试");

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                BeanUtils.copyNonNullProperties(source, null);
            });
        }

        @Test
        @DisplayName("应该处理源对象为null的情况")
        void should_handle_null_source_object() {
            // Given
            TestEntity target = new TestEntity();
            target.setName("原始值");

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                BeanUtils.copyNonNullProperties(null, target);
            });
        }
    }
}