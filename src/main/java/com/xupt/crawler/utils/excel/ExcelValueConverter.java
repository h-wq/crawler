package com.xupt.crawler.utils.excel;

public interface ExcelValueConverter {

    String converter(Object o);


    class DefaultValueConverter implements ExcelValueConverter {

        @Override
        public String converter(Object o) {
            if (o == null) {
                return "";
            }
            return o.toString();
        }
    }
}
