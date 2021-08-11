package com.xupt.crawler.utils;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OkHttpClients {

    private static final Config CONFIG = ConfigFactory.load();

    private static final int CONNECT_TIMEOUT;

    private static final int WRITE_TIMEOUT;

    private static final int READ_TIMEOUT;

    private static final int RETRY_NUM;

    static {
        CONNECT_TIMEOUT = CONFIG.hasPath("httpClient.connectTimeout.milliseconds") ? CONFIG.getInt("httpClient.connectTimeout.milliseconds") : 10000;
        WRITE_TIMEOUT = CONFIG.hasPath("httpClient.writeTimeout.milliseconds") ? CONFIG.getInt("httpClient.writeTimeout.milliseconds") : 10000;
        READ_TIMEOUT = CONFIG.hasPath("httpClient.readTimeout.milliseconds") ? CONFIG.getInt("httpClient.readTimeout.milliseconds") : 10000;
        RETRY_NUM = CONFIG.hasPath("httpClient.retryNum") ? CONFIG.getInt("httpClient.retryNum") : 2;
    }

    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .addInterceptor(new RetryInterceptor(RETRY_NUM))
            .build();

    public static OkHttpClient getInstance() {
        return OK_HTTP_CLIENT;
    }

    /**
     * 重试拦截器
     */
    @Slf4j
    static class RetryInterceptor implements Interceptor {

        private final int retryNum;

        public RetryInterceptor(int retryNum) {
            this.retryNum = retryNum;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = chain.proceed(request);
            int requestNum = 1;
            while (!response.isSuccessful() && requestNum <= retryNum) {
                log.info("request failed, retrying. retryNum:{}", requestNum);
                response = chain.proceed(request);
                requestNum++;
            }
            return response;
        }
    }
}
