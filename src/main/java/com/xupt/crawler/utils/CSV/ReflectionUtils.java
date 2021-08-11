package com.xupt.crawler.utils.CSV;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

abstract class ReflectionUtils {

    public static <T> T newInstance(Class<T> tClass) {
        try {
            return tClass.newInstance();
        } catch (Exception e) {
            String msg = TextUtils.format("Create new instance of class {} failed", tClass.getSimpleName());
            throw new RuntimeException(msg, e);
        }
    }

    public static void setField(Object target, Field field, Object value) {
        try {
            setFieldBySetter(target, field, value);
        } catch (Exception e1) {
            try {
                setFieldDirectly(target, field, value);
            } catch (Exception e2) {
                String msg = TextUtils.format("Set field failed. field: {}, value: {}", field.getName(), value);
                throw new RuntimeException(msg, e2);
            }
        }
    }

    private static void setFieldBySetter(Object target, Field field, Object value) throws Exception {
        String fieldName = StringUtils.capitalize(field.getName());
        String setterName = "set" + fieldName;
        Class<?> tClass = target.getClass();
        Method setter = tClass.getDeclaredMethod(setterName, field.getType());
        setter.invoke(target, value);
    }

    private static void setFieldDirectly(Object target, Field field, Object value) throws Exception {
        boolean accessible = field.isAccessible();
        if (!accessible) {
            field.setAccessible(true);
        }
        field.set(target, value);
        if (!accessible) {
            field.setAccessible(false);
        }
    }
}
