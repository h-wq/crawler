package com.xupt.crawler.service;

public interface CrawlerService {

    String getHtml(String url, String cookie, boolean getOrPost);
}
