package com.xupt.crawler.controller.page;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private int pageNum;

    private int pageSize;

    private long itemCount;

    private int pageCount;

    private List<T> items;
}
