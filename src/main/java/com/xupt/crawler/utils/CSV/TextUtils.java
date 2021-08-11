package com.xupt.crawler.utils.CSV;

import org.slf4j.helpers.MessageFormatter;

public abstract class TextUtils {

    public static String format(String msg, Object... args) {
        return MessageFormatter.arrayFormat(msg, args).getMessage();
    }
}
