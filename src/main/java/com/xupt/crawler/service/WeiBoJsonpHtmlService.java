package com.xupt.crawler.service;

import com.xupt.crawler.controller.resp.WeiboDomain;

import java.util.List;

public interface WeiBoJsonpHtmlService {

    int parsePageCount(String html);

    int parsePageSize(String html);

    List<WeiboDomain> parseData(String html);
}
