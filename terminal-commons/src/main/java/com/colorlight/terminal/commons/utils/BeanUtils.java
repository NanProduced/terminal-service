package com.colorlight.terminal.commons.utils;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.util.HashSet;
import java.util.Set;

/**
 * Bean工具类，封装了Spring的BeanUtils，提供了增强的属性操作功能。
 *
 * @author Nan
 */
public class BeanUtils {

    private BeanUtils() {
        throw new TechnicalException(TechErrorCode.INSTANTIATION_IS_PROHIBITED);
    }

    /**
     * 将源对象的非空属性复制到目标对象。
     * 这个方法解决了Spring BeanUtils在复制时会覆盖目标对象非空属性的问题。
     *
     * @param source 源对象，从中复制属性
     * @param target 目标对象，属性将被复制到这里
     */
    public static void copyNonNullProperties(Object source, Object target) {
        org.springframework.beans.BeanUtils.copyProperties(source, target, getNullPropertyNames(source));
    }

    /**
     * 获取指定对象中所有值为null的属性名。
     *
     * @param source 要检查的对象
     * @return 包含所有null属性名的字符串数组
     */
    private static String[] getNullPropertyNames(Object source) {
        BeanWrapper src = new BeanWrapperImpl(source);
        PropertyDescriptor[] pds = src.getPropertyDescriptors();

        Set<String> emptyNames = new HashSet<>();
        for (PropertyDescriptor pd : pds) {
            Object val = src.getPropertyValue(pd.getName());
            if (val == null) {
                emptyNames.add(pd.getName());
            }
        }
        String[] result = new String[emptyNames.size()];
        return emptyNames.toArray(result);
    }


}
