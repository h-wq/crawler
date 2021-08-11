package com.xupt.crawler.utils.CSV;

import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CSVCell {

    /**
     * 列名
     */
    String value() default "";

    /**
     * 是否必须
     */
    boolean required() default true;

    String defaultValue() default StringUtils.EMPTY;

    /**
     * Date, LocalDate类型字段的格式
     * 多种格式任意一种匹配即可
     */
    String[] format() default {};

    /**
     * 字符串长度限制
     */
    int maxLength() default 5000;

    /**
     * 字符串超长是否允许截断，默认不允许，直接抛出异常
     */
    boolean allowCutOff() default false;

    /**
     * 属性顺序
     */
    int order() default 0;
}