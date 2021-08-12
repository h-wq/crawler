package com.xupt.crawler.controller.resp;

import com.xupt.crawler.utils.CSV.CSVCell;
import com.xupt.crawler.utils.excel.ExcelColumn;
import lombok.Data;

@Data
public class WeiboDomain {

    public static String[] defaultHeaders = {"uid", "name", "txt", "img", "relayNum", "comment", "likes", "relayNames"};

    @ExcelColumn(header = "uid", order = 1)
    @CSVCell(value = "uid", order = 1)
    private String uid;

    @ExcelColumn(header = "name", order = 2)
    @CSVCell(value = "name", order = 2)
    private String name;

    @ExcelColumn(header = "fans", order = 3)
    @CSVCell(value = "fans", order = 3)
    private String fans;

    @ExcelColumn(header = "address", order = 4)
    @CSVCell(value = "address", order = 4)
    private String address;

    @ExcelColumn(header = "txt", order = 5)
    @CSVCell(value = "txt", order = 5)
    private String txt;

    @ExcelColumn(header = "img", order = 6)
    @CSVCell(value = "img", order = 6)
    private String img;

    @ExcelColumn(header = "date", order = 7)
    @CSVCell(value = "date", order = 7)
    private String date;

    @ExcelColumn(header = "relayNum", order = 8)
    @CSVCell(value = "relayNum", order = 8)
    private String relayNum;

    @ExcelColumn(header = "comment", order = 9)
    @CSVCell(value = "comment", order = 9)
    private String comment;

    @ExcelColumn(header = "likes", order = 10)
    @CSVCell(value = "likes", order = 10)
    private String likes;

    @ExcelColumn(header = "relayUidList", order = 11)
    @CSVCell(value = "relayUidList", order = 11)
    private String relayUidList;

    @ExcelColumn(header = "relayNames", order = 12)
    @CSVCell(value = "relayNames", order = 12)
    private String relayNames;
}
