package com.xupt.crawler.utils.CSV;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public abstract class CSVUtils {

    private static final String DEFAULT_LOCAL_DATE_FORMAT = "yyyy-MM-dd";
    private static final String DEFAULT_DATE_FORMAT = "yyyyMMdd hhMMss";

    @Data
    @Builder
    static class ParseOptions {
        int skipHeadRows;
        int skipTailRows;
    }

    /**
     * 读取CSV文件 并将其解析成指定类型的对象列表
     */
    public static <T> List<T> parseAsList(File file, Class<T> itemClass) {
        try {
            Reader reader = new FileReader(file);
            return parseAsList(reader, itemClass);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("file not found.", e);
        }
    }

    /**
     * 读取CSV格式输入流 并将其解析成指定类型的对象列表
     */
    public static <T> List<T> parseAsList(Reader reader, Class<T> itemClass) {
        return parseAsList(null, reader, itemClass);
    }

    public static <T> List<T> parseAsList(String[] headers, Reader reader, Class<T> itemClass) {
        try {
            CSVParser parser = createParser(reader, headers == null, headers);
            List<CSVCellField> fields = getFieldByAnnotation(itemClass);
            List<T> list = new ArrayList<>();
            parser.iterator().forEachRemaining(record -> {
                T row = ReflectionUtils.newInstance(itemClass);
                fields.forEach(csvCellField -> {
                    try {
                        String value = record.get(csvCellField.getHeader());
                        injectValues(csvCellField, row, value);
                    } catch (Exception e) {
                        if (csvCellField.isRequired()) {
                            throw new RuntimeException(TextUtils.format("Inject required field {} failed. row: {}", csvCellField.getHeader(), record.toString()), e);
                        }
                    }
                });
                list.add(row);
            });
            return list;
        } catch (IOException e) {
            throw new RuntimeException(TextUtils.format("Failed to parse CSV file as List<{}>", itemClass.getSimpleName()), e);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    /**
     * 将CSV文件解析成List
     *
     * @param file     文件
     * @param function 解析function
     * @param options  额外参数
     * @param <T>      解析返回数据泛型
     * @return 返回数据
     */
    public static <T> List<T> parseAsList(File file, Function<CSVRecord, T> function, ParseOptions options) {
        try (Reader reader = new FileReader(file);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT)
        ) {
            List<CSVRecord> csvRecords = csvParser.getRecords();
            if (options != null) {
                csvRecords = csvRecords.subList(options.skipHeadRows, csvRecords.size() - options.skipTailRows);
            }
            if (csvRecords.isEmpty()) {
                return new ArrayList<>();
            }
            return csvRecords.stream()
                    .map(function)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (FileNotFoundException e) {
            throw new RuntimeException("file not found.", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSV file as List<>", e);
        }
    }

    public static <T> List<T> parseAsList(File file, Function<CSVRecord, T> function) {
        return parseAsList(file, function, null);
    }

    /**
     * 读取CSV格式输入流 第一列解析为属性名，第二列解析为属性值
     */
    public static <T> T parseAsObject(Reader reader, Class<T> tClass) throws IOException {
        Map<String, String> map = new HashMap<>();
        CSVParser parser = CSVFormat.DEFAULT.parse(reader);
        parser.forEach(record -> {
            String key = record.get(0);
            String value = record.get(1);
            map.put(key, value);
        });
        return buildDataBean(tClass, map);
    }

    private static <T> T buildDataBean(Class<T> tClass, Map<String, String> map) {
        List<CSVCellField> fields = getFieldByAnnotation(tClass);
        try {
            T row = tClass.newInstance();
            fields.forEach(csvCellField -> {
                try {
                    injectValues(csvCellField, row, map.get(csvCellField.getHeader()));
                } catch (Exception e) {
                    if (csvCellField.isRequired()) {
                        throw e;
                    }
                }
            });
            return row;
        } catch (Exception e) {
            throw new RuntimeException("build data bean failed", e);
        }
    }

    private static CSVParser createParser(Reader reader, boolean firstRecordAsRecord, String[] headers) throws IOException {
        CSVFormat format = firstRecordAsRecord ?
                CSVFormat.DEFAULT.withFirstRecordAsHeader() : CSVFormat.DEFAULT.withHeader(headers);
        return format.parse(reader);
    }

    private static <T> void injectValues(CSVCellField csvCellField, T target, String originValue) {
        Object value = convert(csvCellField, originValue);
        ReflectionUtils.setField(target, csvCellField.field, value);
    }

    private static Object convert(CSVCellField csvCellField, String strValue) {
        Class fieldClazz = csvCellField.getField().getType();
        strValue = strValue.trim();
        if (Number.class.isAssignableFrom(fieldClazz) || fieldClazz.isPrimitive()) {
            return parseNumber(csvCellField, strValue);
        } else if (fieldClazz == Boolean.class || fieldClazz == boolean.class) {
            return Boolean.valueOf(strValue);
        } else if (fieldClazz == Date.class) {
            return parseDate(csvCellField, strValue);
        } else if (fieldClazz == LocalDate.class) {
            return parseLocalDate(csvCellField, strValue);
        } else {
            return strValue;
        }
    }

    private static Object parseNumber(CSVCellField csvCellField, String strValue) {
        try {
            Class fieldClazz = csvCellField.getField().getType();
            Number number = NumberFormat.getNumberInstance(Locale.US).parse(strValue);
            if (byte.class == fieldClazz || Byte.class == fieldClazz) {
                return number.byteValue();
            } else if (int.class == fieldClazz || Integer.class == fieldClazz) {
                return number.intValue();
            } else if (short.class == fieldClazz || Short.class == fieldClazz) {
                return number.shortValue();
            } else if (long.class == fieldClazz || Long.class == fieldClazz) {
                return number.longValue();
            } else if (float.class == fieldClazz || Float.class == fieldClazz) {
                return number.floatValue();
            } else if (double.class == fieldClazz || Double.class == fieldClazz) {
                return number.doubleValue();
            } else if (BigDecimal.class == fieldClazz) {
                return new BigDecimal(strValue);
            }
            return number;
        } catch (ParseException e) {
            String msg = TextUtils.format("invalid number {} for field {}", strValue, csvCellField.getField().getName());
            throw new RuntimeException(msg, e);
        }
    }

    private static LocalDate parseLocalDate(CSVCellField csvCellField, String strValue) {
        String[] formats = getFormats(csvCellField, DEFAULT_LOCAL_DATE_FORMAT);
        for (String format : formats) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            try {
                return LocalDate.parse(strValue, formatter);
            } catch (Exception ignored) {

            }
        }
        throw new RuntimeException("invalid date string " + strValue);
    }

    private static Date parseDate(CSVCellField csvCellField, String strValue) {
        String[] formats = getFormats(csvCellField, DEFAULT_DATE_FORMAT);
        for (String format : formats) {
            SimpleDateFormat fmt = new SimpleDateFormat(format);
            try {
                return fmt.parse(strValue);
            } catch (ParseException ignored) {
            }
        }
        throw new RuntimeException("invalid date string " + strValue);
    }

    private static String[] getFormats(CSVCellField csvCellField, String dftFormat) {
        if (csvCellField.getFormat() == null || csvCellField.getFormat().length == 0) {
            return new String[]{dftFormat};
        } else {
            return csvCellField.getFormat();
        }
    }

    private static List<CSVCellField> getFieldByAnnotation(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        return Stream.of(fields)
                .filter(f -> f.getAnnotation(CSVCell.class) != null).map(f -> {
                    CSVCell cell = f.getAnnotation(CSVCell.class);
                    return new CSVCellField(cell.value(), cell.required(), f, cell.format());
                }).collect(Collectors.toList());
    }

    public static <T> void exportCSV(String[] headers, Collection<T> dataSet, File file) {
        try {
            exportCSV(headers, dataSet, new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("export CSV failed", e);
        }
    }

    public static <T> void exportCSV(String[] headers, Collection<T> dataSet, OutputStream out) {
        exportCSV(headers, dataSet, out, true);
    }

    public static <T> void exportCSVWithoutHeaders(String[] headers, Collection<T> dataSet, OutputStream out) {
        exportCSV(headers, dataSet, out, false);
    }

    public static <T> void exportCSVWithParam(String[] headers, Collection<T> dataSet, OutputStream out, ExportParam param) throws IOException {
        if (param.isWithBomHead()) {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        }
        exportCSV(headers, dataSet, out, param.isWithHeaders());
    }

    private static <T> void exportCSV(String[] headers, Collection<T> dataSet, OutputStream out, boolean withHeaders) {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out))) {
            if (withHeaders && headers != null) {
                for (int i = 0; i < headers.length; i++) {
                    bw.append(headers[i]);
                    if (i < headers.length - 1) {
                        bw.append(",");
                    }
                }
                bw.newLine();
            }
            if (dataSet.isEmpty()) {
                return;
            }
            T data = dataSet.iterator().next();
            List<Field> fieldList = sortFieldByAnnotation(data.getClass(), headers);
            CSVFile csvFile = data.getClass().getAnnotation(CSVFile.class);
            for (T row : dataSet) {
                try {
                    for (int i = 0; i < fieldList.size(); i++) {
                        Field field = fieldList.get(i);
                        setCellValue(bw, field, row);
                        if (i < fieldList.size() - 1) {
                            bw.append(",");
                        }
                    }
                    if (csvFile != null) {
                        for (int i = 0; i < csvFile.appendBlankSize(); ++i) {
                            bw.append(",");
                        }
                    }
                    bw.newLine();
                } catch (IllegalArgumentException e) {
                    throw e;
                } catch (Exception e) {
                    log.error(e.toString(), e);
                }
            }
        } catch (IOException e) {
            log.error(e.toString(), e);
        }
    }

    private static <T> void setCellValue(BufferedWriter bw, Field field, T row) throws Exception {
        field.setAccessible(true);
        Object value = field.get(row);
        String textValue;
        CSVCell ann = field.getAnnotation(CSVCell.class);
        if (value instanceof Date) {
            Date date = (Date) value;
            DateTimeFormatter dateTimeFormatter = ann.format().length == 0 ? DateTimeFormatter.ISO_DATE_TIME :
                    DateTimeFormatter.ofPattern(ann.format()[0]);
            textValue = ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("US/Eastern")).format(getFormatter(field, dateTimeFormatter));
        } else if (value instanceof LocalDate) {
            LocalDate date = (LocalDate) value;
            DateTimeFormatter dateTimeFormatter = ann.format().length == 0 ? DateTimeFormatter.ISO_DATE :
                    DateTimeFormatter.ofPattern(ann.format()[0]);
            textValue = date.format(getFormatter(field, dateTimeFormatter));
        } else {
            String empty = "";
            if (ann != null) {
                empty = ann.defaultValue();
            }
            textValue = value == null ? empty : value.toString();
            if (textValue != null && ann != null && textValue.length() > ann.maxLength()) {
                if (ann.allowCutOff()) {
                    textValue = textValue.substring(0, ann.maxLength());
                } else {
                    throw new IllegalArgumentException(
                            String.format("Convert to csv file error, text %s 's length longer than the  max length %d",
                                    textValue, ann.maxLength()));
                }
            }
        }
        bw.append(textValue);
    }

    private static DateTimeFormatter getFormatter(Field field, DateTimeFormatter defaultFormatter) {
        DateTimeFormatter formatter = defaultFormatter;
        DateTimeFormat format = field.getAnnotation(DateTimeFormat.class);
        if (format != null) {
            if (!StringUtils.isEmpty(format.pattern())) {
                formatter = DateTimeFormatter.ofPattern(format.pattern());
            } else {
                switch (format.iso()) {
                    case TIME:
                        formatter = DateTimeFormatter.ISO_LOCAL_TIME;
                        break;
                    case DATE_TIME:
                        formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                        break;
                    case DATE:
                        formatter = DateTimeFormatter.ISO_LOCAL_DATE;
                        break;
                    default:
                        break;
                }
            }
        }
        return formatter;
    }

    private static List<Field> sortFieldByAnnotation(Class<?> clazz, String[] headers) {
        Field[] fields = clazz.getDeclaredFields();
        Map<String, Field> fieldMap = Stream.of(fields)
                .filter(f -> f.getAnnotation(CSVCell.class) != null)
                .collect(Collectors.toMap(f -> {
                    CSVCell cell = f.getAnnotation(CSVCell.class);
                    return cell.value();
                }, Function.identity()));
        return Stream.of(headers).map(fieldMap::get).collect(Collectors.toList());
    }

    @Data
    private static class CSVCellField {

        private final String header;
        private final boolean required;
        private final Field field;
        private final String[] format;
    }

    @Data
    public static class ExportParam {
        private boolean withHeaders;
        private boolean withBomHead;

        public static ExportParam of(boolean withBomHead, boolean withHeaders) {
            ExportParam param = new ExportParam();
            param.setWithBomHead(withBomHead);
            param.setWithHeaders(withHeaders);
            return param;
        }
    }
}
