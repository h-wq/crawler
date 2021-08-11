package com.xupt.crawler.service.impl;

import com.xupt.crawler.service.CrawlerService;
import com.xupt.crawler.utils.OkHttpClients;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class CrawlerServiceImpl implements CrawlerService {

    @Override
    public String getHtml(String url, String cookie) {
        OkHttpClient okHttpClient = OkHttpClients.getInstance();
        Request request = getRequest(url, cookie);
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().string();
            } else {
                throw new RuntimeException(String.format("http response is not ok, url: %s code: %s response: %s",
                        url, response.code(), response.body().string()));
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("http request failed. request: %s exception: %s", url, e));
        }
    }

    private Request getRequest(String url, String cookie) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Safari/537.36")
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9")
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=utf-8");
        if (!StringUtils.isEmpty(cookie)) {
            builder = builder.header(HttpHeaders.COOKIE, cookie).get();
        }
        return builder.build();
    }
}
