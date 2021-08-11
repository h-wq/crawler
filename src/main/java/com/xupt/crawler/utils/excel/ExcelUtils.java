package com.xupt.crawler.utils.excel;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * <h2>Usage</h2>
 * <pre> {@code
 *  class Demo {
 *     // define model class
 *     @Data
 *     public static class A {
 *         @ExcelColumn(header = "a1", order = 0)
 *         private String a1 = "test a1";
 *         @ExcelColumn(order = 1)
 *         private B b;
 *         @ExcelColumn(order = 2)
 *         private List<C> cs;
 *         @ExcelColumn(header = "d", order = 3)
 *         private int d = 1;
 *         @ExcelColumn(header = "longs")
 *         private List<Long> es = Lists.newArrayList(1L, 2L);
 *     }
 *
 *     @Data
 *     public static class B {
 *         @ExcelColumn(header = "b1 of B")
 *         private String b1 = "test b1";
 *         @ExcelColumn(header = "b2 of B")
 *         private String b2 = "test b2";
 *     }
 *
 *     @Data
 *     public static class C {
 *         @ExcelColumn(header = "c1 of C")
 *         private String c1 = "test c1";
 *         @ExcelColumn(header = "c2 of C")
 *         private String c2 = "test c2";
 *     }
 *
 *     // write list<A> to excel file
 *     public static void main() {
 *          A obj = new A();
 *         obj.setB(new B());
 *         obj.setCs(Lists.newArrayList(new C(), new C(), new C()));
 *         FileOutputStream outputStream = new FileOutputStream("/tmp/test.xlsx");
 *         ExcelUtils.write(outputStream, Lists.newArrayList(obj, obj));
 *     }
 *  }
 * }</pre>
 * <p>
 * the format of output file as below:
 * |a1      |b1 of B |b2 of B |c1 of C |c2 of C |d |longs|
 * |test a1 |test b1 |test b2 |test c1 |test c2 |1 |1    |
 * |        |        |        |test c1 |test c2 |  |2    |
 * |        |        |        |test c1 |test c2 |  |     |
 * |test a1 |test b1 |test b2 |test c1 |test c2 |1 |1    |
 * |        |        |        |test c1 |test c2 |  |2    |
 * |        |        |        |test c1 |test c2 |  |     |
 *
 * @author yoje
 * Date    2019/3/3
 * @see ExcelColumn
 */
@Slf4j
public abstract class ExcelUtils {

    private ExcelUtils() {
    }

    private static final String xls = "xls";

    private static final String xlsx = "xlsx";


    @Nonnull
    public static <T> Stream<T> read(InputStream in, String fileName, Class<T> itemClass) throws IllegalArgumentException {
        return read(in, fileName, itemClass, 0, 0);
    }

    public static <T> Stream<T> read(InputStream in, String fileName, Class<T> itemClass,  int sheetNum, int headRow) throws IllegalArgumentException {
        Workbook workbook = getWorkBook(in, fileName);
        Sheet sheet = workbook.getSheetAt(sheetNum);
        List<String> headers = new ArrayList<>();
        sheet.getRow(headRow).iterator().forEachRemaining(cell -> {
            headers.add(cell.getStringCellValue());
        });
        List<ColumnDesc> columnDescs = collectFieldByHeaders(itemClass, headers.toArray(new String[0]));

        if (CollectionUtils.isEmpty(columnDescs)) {
            throw new IllegalArgumentException("no valid fields.");
        }
        return IntStream.range(headRow + 1, sheet.getLastRowNum() + 1)
                .mapToObj(rowNum -> {
                    Row row = sheet.getRow(rowNum);
                    if (isEmpty(row)) {
                        return null;
                    }
                    T item = newInstance(itemClass);
                    for (int i = 0; i < columnDescs.size(); i++) {
                        ColumnDesc columnDesc = columnDescs.get(i);
                        if (columnDesc != null) {
                            try {
                                columnDesc.setter.invoke(item, getCellValue(row.getCell(i), columnDesc.type,
                                        getColumnName(columnDesc)));
                            } catch (InvocationTargetException | IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    return item;
                })
                .filter(Objects::nonNull);
    }

    public static void write(HttpServletResponse response, List<?> items, String fileName) {
        SheetData sheetData = new SheetData(null, items, null);
        writeSheets(response, Lists.newArrayList(sheetData), fileName);
    }

    public static void writeSheets(HttpServletResponse response, List<SheetData> sheetDatas, String fileName) {
        response.setHeader("Pragma", "private");
        response.setHeader("Cache-Control", "private, must-revalidate");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + ".xlsx\"");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try {
            writeSheets(response.getOutputStream(), sheetDatas);
        } catch (IOException e) {
            log.error("downlod excel file exception.", e);
            throw new RuntimeException("downlod excel file exception", e);
        }
    }

    public static void writeSheets(OutputStream outputStream, List<SheetData> sheetDatas) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            sheetDatas.forEach(sheetData ->
                    write(workbook, sheetData.getName(), sheetData.getItems(),
                            sheetData.getIgnoredColumn() == null ? null : Sets.newHashSet(sheetData.getIgnoredColumn())));
            workbook.write(outputStream);
        }
    }

    public static void write(OutputStream outputStream, List<?> items) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            write(workbook, items);
            workbook.write(outputStream);
        }
    }

    public static void write(Workbook workbook, List<?> items) {
        if (CollectionUtils.isNotEmpty(items)) {
            writeSheet(workbook, items);
        }
    }

    public static void write(Workbook workbook, String sheetName, List<?> items, Set<String> ignoredColumns) {
        if (CollectionUtils.isNotEmpty(items)) {
            writeSheet(workbook, sheetName, items, ignoredColumns);
        }
    }

    private static List<ColumnDesc> collectFieldByHeaders(Class<?> clz, String[] headers) {
        List<ColumnDesc> columnDescs = columnDescs(clz);
        return Arrays.stream(headers)
                .map(header -> getColumnDesc(header, columnDescs))
                .collect(Collectors.toList());
    }

    private static ColumnDesc getColumnDesc(String header, List<ColumnDesc> columnDescs) {
        for (ColumnDesc columnDesc : columnDescs) {
            if (columnDesc.propertyName.equals(header) || columnDesc.header.equals(header)) {
                return columnDesc;
            }
        }
        return null;
    }


    private static Workbook getWorkBook(InputStream in, String fileName) {
        Workbook workbook = null;
        if (fileName == null) {
            fileName = xls;
        }
        try {
            if (fileName.endsWith(xls)) {
                workbook = new HSSFWorkbook(in);
            } else if (fileName.endsWith(xlsx)) {
                workbook = new XSSFWorkbook(in);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Excel file", e);
        }
        return workbook;
    }

    private static <T> T newInstance(Class<T> clz) {
        try {
            return clz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object getCellValue(Cell cell, Class<?> clz, String columnName) {
        if (cell == null) {
            return null;
        }
        CellType cellType = cell.getCellTypeEnum();

        if (cellType == CellType.BLANK) {
            return null;
        } else if (cellType == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }

        String cellStringValue = getCellStringValue(cell);
        try {
            if (Strings.isNullOrEmpty(cellStringValue)) {
                return null;
            }
            if (clz.equals(String.class)) {
                return cellStringValue;
            }
            if (clz.equals(Long.class) || clz.equals(long.class)) {
                return Long.valueOf(cellStringValue);
            }
            if (clz.equals(Integer.class) || clz.equals(int.class)) {
                return Integer.valueOf(cellStringValue);
            }
            if (clz.equals(Short.class) || clz.equals(short.class)) {
                return Short.valueOf(cellStringValue);
            }
            if (clz.equals(Byte.class) || clz.equals(byte.class)) {
                return Byte.valueOf(cellStringValue);
            }
            if (clz.equals(Float.class) || clz.equals(float.class)) {
                return Float.valueOf(cellStringValue);
            }
            if (clz.equals(Double.class) || clz.equals(double.class)) {
                return Double.valueOf(cellStringValue);
            }
            if (clz.equals(Boolean.class) || clz.equals(boolean.class)) {
                if (cellStringValue.equalsIgnoreCase("false")) {
                    return false;
                } else if (cellStringValue.equalsIgnoreCase("true")) {
                    return true;
                }
                throw new IllegalArgumentException(String.format("data type is illegal. column %s value %s", columnName,
                        cellStringValue));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("data exception:%s column %s value %s", e.getMessage(),
                    columnName, cellStringValue), e);
        }
        throw new IllegalArgumentException(String.format("unsupported data type:%s column %s value %s", clz, columnName,
                cellStringValue));
    }

    private static String getCellStringValue(Cell cell) {
        return new DataFormatter().formatCellValue(cell);
    }

    private static String getColumnName(ColumnDesc columnDesc) {
        return columnDesc.getHeader() != null ? columnDesc.getHeader() : columnDesc.getPropertyName();
    }

    public static List<ColumnDesc> sortColumnDescByOrder(Class<?> clz) {
        List<ColumnDesc> columnDescs = columnDescs(clz);
        columnDescs.sort(Comparator.comparingInt(o -> o.order));
        return columnDescs;
    }

    private static void writeSheet(Workbook workbook, List<?> datas) {
        Sheet sheet = workbook.createSheet();
        List<ColumnDesc> columnDescs = sortColumnDescByOrder(datas.get(0).getClass());
        setColumnStyle(workbook, sheet, columnDescs);
        writeHeader(sheet, columnDescs);
        writeContens(sheet, datas, columnDescs);
    }

    private static void writeSheet(Workbook workbook, String sheetName, List<?> datas, Set<String> ignoredColumns) {
        Sheet sheet;
        if (StringUtils.isNotBlank(sheetName)) {
            sheet = workbook.createSheet(sheetName);
        } else {
            sheet = workbook.createSheet();
        }
        writeSheet(workbook, sheet, datas, ignoredColumns);
    }

    private static void writeSheet(Workbook workbook, Sheet sheet, List<?> datas, Set<String> ignoredColumns) {
        List<ColumnDesc> columnDescs = sortColumnDescByOrder(datas.get(0).getClass());
        setColumnStyle(workbook, sheet, columnDescs);
        List<ColumnDesc> filteredColumnDescs = columnDescs;
        if (CollectionUtils.isNotEmpty(ignoredColumns)) {
            filteredColumnDescs = columnDescs.stream().filter(cd -> !ignoredColumns.contains(cd.getPropertyName()))
                    .collect(Collectors.toList());
        }
        writeHeader(sheet, filteredColumnDescs);
        writeContens(sheet, datas, filteredColumnDescs);
    }

    private static void writeHeader(Sheet sheet, List<ColumnDesc> columnDescs) {
        Row row = sheet.createRow(0);
        Cell firstCell = row.createCell(0);
        writeHeader(firstCell, columnDescs);
    }

    private static Cell writeHeader(Cell startCell, List<ColumnDesc> columnDescs) {
        Cell currentCell = startCell;
        Cell endCell = null;
        for (ColumnDesc columnDesc : columnDescs) {
            endCell = writeHeader(currentCell, columnDesc);
            currentCell = getCell(endCell.getSheet(), endCell.getRowIndex(), endCell.getColumnIndex() + 1);
        }
        return endCell;
    }

    private static Cell writeHeader(Cell startCell, ColumnDesc columnDesc) {
        if (!columnDesc.isBasicType()) {
            return writeHeader(startCell, columnDesc.getChilds());
        } else {
            startCell.setCellValue(columnDesc.getHeader());
            return startCell;
        }
    }

    /**
     * @param objs
     * @param columnDescs columnDesc of type of element in objs
     */
    private static void writeContens(Sheet sheet, List<?> objs, List<ColumnDesc> columnDescs) {
        Cell startCell = sheet.createRow(1).createCell(0);
        writeNonBasicDataList(objs, startCell, columnDescs);
    }

    /**
     * @param objs
     * @param columnDescs columnDesc of type of element in objs
     */
    private static Cell writeNonBasicDataList(Iterable<?> objs, Cell startCell, List<ColumnDesc> columnDescs) {
        Cell currentCell = startCell;
        Cell endCell = startCell;
        for (Object obj : objs) {
            endCell = writeNonBasicType(columnDescs, currentCell, obj);
            currentCell = getCell(endCell.getSheet(), endCell.getRowIndex() + 1, startCell.getColumnIndex());
        }
        return endCell;
    }

    /**
     * @param objs
     * @param columnDesc columnDesc of type of objs
     */
    private static Cell writeBasicDataList(Iterable<?> objs, Cell startCell, ColumnDesc columnDesc) {
        Cell currentCell = startCell;
        Cell endCell = startCell;
        for (Object obj : objs) {
            endCell = writeFieldWithBasicType(columnDesc, currentCell, obj);
            currentCell = getCell(endCell.getSheet(), endCell.getRowIndex() + 1, startCell.getColumnIndex());
        }
        return endCell;
    }


    /**
     * @param columnDesc 要写的字段详情
     * @param startCell  写入的起始cell
     * @param obj        字段所属的对象
     * @return
     */
    private static Cell writeField(ColumnDesc columnDesc, Cell startCell, @Nullable Object obj) {
        Object field = invokeGetter(columnDesc, obj);
        if (columnDesc.isCollection()) {
            Iterable datas = null;
            if (field == null) {
                ArrayList list = new ArrayList();
                list.add(null);
                datas = list;
            } else {
                datas = (Iterable<?>) field;
            }
            if (columnDesc.isBasicType()) {
                return writeBasicDataList(datas, startCell, columnDesc);
            } else {
                return writeNonBasicDataList(datas, startCell, columnDesc.getChilds());
            }
        } else if (!columnDesc.isBasicType()) {
            return writeNonBasicType(columnDesc.getChilds(), startCell, field);
        } else {
            // 基本类型, 直接写
            return writeFieldWithBasicType(columnDesc, startCell, field);
        }
    }

    private static Cell writeFieldWithBasicType(ColumnDesc columnDesc, Cell cell, Object fieldValue) {
        cell.setCellValue(getValue(columnDesc, fieldValue));
        return cell;
    }

    private static Cell writeNonBasicType(List<ColumnDesc> columnDescs, Cell startCell, @Nullable Object obj) {
        Cell currentCell = startCell;
        Cell endCell = null;
        int maxRowIndex = startCell.getRowIndex();
        int maxColumnIndex = startCell.getColumnIndex();
        for (ColumnDesc childColumnDesc : columnDescs) {
            endCell = writeField(childColumnDesc, currentCell, obj);
            maxRowIndex = Math.max(maxRowIndex, endCell.getRowIndex());
            maxColumnIndex = Math.max(maxColumnIndex, endCell.getColumnIndex());
            currentCell = getCell(currentCell.getSheet(), currentCell.getRowIndex(),
                    endCell.getColumnIndex() + 1);
        }
        return getCell(startCell.getSheet(), maxRowIndex, maxColumnIndex);
    }

    private static Object invokeGetter(ColumnDesc columnDesc, Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return columnDesc.getGetter().invoke(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getValue(ColumnDesc columnDesc, Object value) {

        try {
            if (value == null) {
                return columnDesc.getDefaultValue();
            }
            ExcelValueConverter excelValueConverter = columnDesc.getExcelValueConverter();
            if (excelValueConverter != null) {
                return excelValueConverter.converter(value);
            }
            return value.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Cell getCell(Sheet sheet, int rowIdx, int columnIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            row = sheet.createRow(rowIdx);
        }
        Cell cell = row.getCell(columnIdx);
        if (cell == null) {
            cell = row.createCell(columnIdx);
        }
        return cell;
    }

    private static List<ColumnDesc> columnDescs(Class<?> clz) {
        try {
            return Arrays.stream(Introspector.getBeanInfo(clz, Object.class)
                    .getPropertyDescriptors())
                    .filter(pd -> Objects.nonNull(pd.getReadMethod()))
                    .map(pd -> {
                        ColumnDesc columnDesc = new ColumnDesc();
                        try {
                            Field field = getField(clz, pd.getName());
                            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                            if (annotation != null) {
                                if (annotation.ignore()) {
                                    return null;
                                }
                                columnDesc.header = annotation.header().equals(ExcelColumn.EMPTY) ? pd.getName() : annotation.header();
                                columnDesc.order = annotation.order();
                                columnDesc.defaultValue = annotation.defaultValue();
                                columnDesc.cellType = annotation.cellType();
                                if (!annotation.valueConverter().equals(ExcelValueConverter.class)) {
                                    columnDesc.excelValueConverter = newInstance(annotation.valueConverter());
                                }
                            } else {
                                columnDesc.header = pd.getName();
                                columnDesc.order = ExcelColumn.DEFAULT_ORDER;
                                columnDesc.defaultValue = ExcelColumn.EMPTY;
                            }
                            columnDesc.type = field.getType();
                            columnDesc.getter = pd.getReadMethod();
                            columnDesc.setter = pd.getWriteMethod();
                            columnDesc.propertyName = pd.getName();

                            if (isCollection(field.getType())) {
                                columnDesc.setCollection(true);
                                Class<?> childClz =
                                        (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];

                                if (isCollection(childClz)) {
                                    throw new IllegalArgumentException("do not support List<List<?>> type");
                                } else if (isBasicType(childClz)) {
                                    columnDesc.setBasicType(true);
                                } else {
                                    columnDesc.setBasicType(false);
                                    columnDesc.setChilds(sortColumnDescByOrder(childClz));
                                }
                            } else if (!isBasicType(field.getType())) {
                                columnDesc.setBasicType(false);
                                columnDesc.setChilds(sortColumnDescByOrder(field.getType()));
                            }
                            return columnDesc;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setColumnStyle(Workbook workbook, Sheet sheet, List<ColumnDesc> columnDescs) {
        for (int i = 0; i < columnDescs.size(); i++) {
            String cellFormat = columnDescs.get(i).cellType;
            if (Strings.isNullOrEmpty(cellFormat)) {
                continue;
            }
            DataFormat fmt = workbook.createDataFormat();
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(fmt.getFormat(cellFormat));
            sheet.setDefaultColumnStyle(i, textStyle);
        }
    }

    private static boolean isEmpty(Row row) {
        if (row == null) {
            return true;
        }
        if (row.getLastCellNum() <= 0) {
            return true;
        }
        for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
            Cell cell = row.getCell(cellNum);
            if (cell != null && cell.getCellTypeEnum() != CellType.BLANK && StringUtils.isNotBlank(cell.toString())) {
                return false;
            }
        }
        return true;
    }

    private static Field getField(@Nonnull Class<?> clazz, @Nonnull String name) {
        Class<?> searchType = clazz;
        while (Object.class != searchType && searchType != null) {
            Field[] fields = searchType.getDeclaredFields();
            for (Field field : fields) {
                if (name.equals(field.getName())) {
                    return field;
                }
            }
            searchType = searchType.getSuperclass();
        }
        throw new IllegalArgumentException(String.format("class %s can not find field %s", clazz.getName(), name));
    }

    private static boolean isCollection(Class<?> clazz) {
        return Iterable.class.isAssignableFrom(clazz);
    }

    private static boolean isBasicType(Class<?> clazz) {
        return clazz.isPrimitive() || clazz.getPackage().getName().contains("java.");
    }

    @Data
    public static class ColumnDesc {
        private Method getter;
        private Method setter;
        private int order;
        // excel 表头名称
        private String header;
        // 对象属性名称
        private String propertyName;
        private Class<?> type;
        private String defaultValue;
        private String cellType;
        private ExcelValueConverter excelValueConverter;
        private boolean isCollection = false;
        private boolean isBasicType = true;
        private List<ColumnDesc> childs = Collections.emptyList();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(propertyName);
            if (!childs.isEmpty()) {
                sb.append(childs.toString());
            }
            return sb.toString();
        }
    }

    @Data
    @AllArgsConstructor
    public static class SheetData {
        private String name;
        private List<?> items;
        List<String> ignoredColumn;
    }

}
