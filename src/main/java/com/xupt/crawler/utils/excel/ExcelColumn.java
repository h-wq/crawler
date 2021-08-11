package com.xupt.crawler.utils.excel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface ExcelColumn {

    String EMPTY = "";
    int DEFAULT_ORDER = 999;

    /**
     * excel表格中的列名称
     * 默认使用字段名称
     */
    String header() default EMPTY;

    /**
     * true表示不导出当前字段
     */
    boolean ignore() default false;

    /**
     * 该字段为空时的默认值
     */
    String defaultValue() default EMPTY;

    /**
     * excel表格中列的顺序,
     * 从小到大排列
     */
    int order() default DEFAULT_ORDER;

    String cellType() default "";

    Class<? extends ExcelValueConverter> valueConverter() default ExcelValueConverter.class;
}
