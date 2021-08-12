package com.xupt.crawler.controller;

import com.xupt.crawler.controller.json.JsonResult;
import com.xupt.crawler.controller.page.PageResult;
import com.xupt.crawler.controller.resp.WeiboDomain;
import com.xupt.crawler.service.CrawlerService;
import com.xupt.crawler.service.WeiBoJsonpHtmlService;
import com.xupt.crawler.utils.CSV.CSVUtils;
import com.xupt.crawler.utils.excel.ExcelUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/wei_bo")
public class WeiBoCrawlerController {

    @Autowired
    private CrawlerService crawlerService;

    @Autowired
    private WeiBoJsonpHtmlService weiBoJsonpHtmlService;

    private final Executor pageParseExecutor = Executors.newWorkStealingPool(10);

    private final ExecutorCompletionService<List<WeiboDomain>> pageParseCompleteService = new ExecutorCompletionService<>(pageParseExecutor);

    @GetMapping("/list")
    public JsonResult<PageResult<WeiboDomain>> list(@RequestParam(name = "q") String q,
                                                    @RequestParam(name = "start_time", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH") LocalDateTime startTime,
                                                    @RequestParam(name = "end_time", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH") LocalDateTime endTime,
                                                    @RequestParam(name = "page", defaultValue = "1")  int page) {
        String cookie = "login_sid_t=de193cdb5705dbbec9845b1ff1066006; cross_origin_proto=SSL; _s_tentry=passport.weibo.com; Apache=2715213965734.049.1628528513163; SINAGLOBAL=2715213965734.049.1628528513163; ULV=1628528513170:1:1:1:2715213965734.049.1628528513163:; SSOLoginState=1628528543; wvr=6; wb_view_log_7548501078=1440*9002; WBtopGlobal_register_version=2021081123; SUB=_2A25MF5bGDeRhGeFL71oU8C_MzDSIHXVvZI8OrDV8PUNbmtAKLRbdkW9NQhCB-Q-sVFtZYUjPosyDSEbvTFK_duQ1; SUBP=0033WrSXqPxfM725Ws9jqgMF55529P9D9WW-2o2RjP0Yzwkqbav6N2vD5JpX5KzhUgL.FoMfShnfeh27S0n2dJLoIp7LxKML1KBLBKnLxKqL1hnLBoMNSKBRSK5pehMR; ALF=1660230166; webim_unReadCount=%7B%22time%22%3A1628696645024%2C%22dm_pub_total%22%3A5%2C%22chat_group_client%22%3A0%2C%22chat_group_notice%22%3A0%2C%22allcountNum%22%3A41%2C%22msgbox%22%3A0%7D";
        String jsonStr = crawlerService.getHtml(getUrl(q, startTime, endTime, null), cookie);
        int pageCount = weiBoJsonpHtmlService.parsePageCount(jsonStr);
        if (page < 1 || page > pageCount) {
            throw new RuntimeException(String.format("param page error, totalPage: %s", pageCount));
        }
        if (page == pageCount) {
            int firstPageSize = weiBoJsonpHtmlService.parsePageSize(jsonStr);
            jsonStr = crawlerService.getHtml(getUrl(q, startTime, endTime, page), cookie);
            List<WeiboDomain> weiboDomains =  weiBoJsonpHtmlService.parseData(jsonStr, cookie);
            PageResult<WeiboDomain> result = new PageResult<>();
            result.setPageNum(page);
            result.setPageSize(weiboDomains.size());
            result.setItemCount((pageCount - 1) * firstPageSize + weiboDomains.size());
            result.setPageCount(pageCount);
            result.setItems(weiboDomains);
            return JsonResult.ok(result);
        } else {
            String lastPageJsonStr = crawlerService.getHtml(getUrl(q, startTime, endTime, pageCount), cookie);
            int lastPageSize =  weiBoJsonpHtmlService.parsePageSize(lastPageJsonStr);
            List<WeiboDomain> weiboDomains;
            if (page == 1) {
                weiboDomains =  weiBoJsonpHtmlService.parseData(jsonStr, cookie);
            } else {
                jsonStr = crawlerService.getHtml(getUrl(q, startTime, endTime, page), cookie);
                weiboDomains =  weiBoJsonpHtmlService.parseData(jsonStr, cookie);
            }
            PageResult<WeiboDomain> result = new PageResult<>();
            result.setPageNum(page);
            result.setPageSize(weiboDomains.size());
            result.setItemCount((pageCount - 1) * weiboDomains.size() + lastPageSize);
            result.setPageCount(pageCount);
            result.setItems(weiboDomains);
            return JsonResult.ok(result);
        }
    }

    @GetMapping("/export")
    public void export(@RequestParam(name = "q") String q,
                       @RequestParam(name = "start_time", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH") LocalDateTime startTime,
                       @RequestParam(name = "end_time", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH") LocalDateTime endTime,
                       HttpServletResponse response) {
        String cookie = "login_sid_t=de193cdb5705dbbec9845b1ff1066006; cross_origin_proto=SSL; _s_tentry=passport.weibo.com; Apache=2715213965734.049.1628528513163; SINAGLOBAL=2715213965734.049.1628528513163; ULV=1628528513170:1:1:1:2715213965734.049.1628528513163:; SSOLoginState=1628528543; wvr=6; wb_view_log_7548501078=1440*9002; WBtopGlobal_register_version=2021081123; SUB=_2A25MF5bGDeRhGeFL71oU8C_MzDSIHXVvZI8OrDV8PUNbmtAKLRbdkW9NQhCB-Q-sVFtZYUjPosyDSEbvTFK_duQ1; SUBP=0033WrSXqPxfM725Ws9jqgMF55529P9D9WW-2o2RjP0Yzwkqbav6N2vD5JpX5KzhUgL.FoMfShnfeh27S0n2dJLoIp7LxKML1KBLBKnLxKqL1hnLBoMNSKBRSK5pehMR; ALF=1660230166; webim_unReadCount=%7B%22time%22%3A1628696645024%2C%22dm_pub_total%22%3A5%2C%22chat_group_client%22%3A0%2C%22chat_group_notice%22%3A0%2C%22allcountNum%22%3A41%2C%22msgbox%22%3A0%7D";
        String jsonStr = crawlerService.getHtml(getUrl(q, startTime, endTime, null), cookie);
        int pageCount = weiBoJsonpHtmlService.parsePageCount(jsonStr);
        List<WeiboDomain> weiboDomains =  weiBoJsonpHtmlService.parseData(jsonStr, cookie);
        List<WeiboDomain> allWeiboDomains = new ArrayList<>(weiboDomains);
        for (int i = 2; i <= pageCount; i++) {
            int finalI = i;
            pageParseCompleteService.submit(() -> {
                String pageJsonStr = crawlerService.getHtml(getUrl(q, startTime, endTime, finalI), cookie);
                return weiBoJsonpHtmlService.parseData(pageJsonStr, cookie);
            });
        }
        for (int i = 2; i <= pageCount; i++) {
            try {
                List<WeiboDomain> pageWeiboDomains = pageParseCompleteService.take().get();
                allWeiboDomains.addAll(pageWeiboDomains);
            } catch (Exception e) {
                log.error("get page data exception. error is ", e);
            }
        }
//        try {
//            response.setHeader("Pragma", "private");
//            response.setHeader("Cache-Control", "private, must-revalidate");
//            response.setHeader("Content-Disposition", "attachment; filename=weiBo.csv");
//            response.setContentType("text/csv;charset=utf-8");
//            CSVUtils.exportCSVWithParam(WeiboDomain.defaultHeaders, allWeiboDomains, response.getOutputStream(), CSVUtils.ExportParam.of(true, true));
//        } catch (IOException e) {
//            throw new RuntimeException("liquidation orders download failed", e);
//        }
        ExcelUtils.write(response, allWeiboDomains, "weiBo");
    }

    private String getUrl(String q, LocalDateTime startTime, LocalDateTime endTime, Integer page) {
        String url = "https://s.weibo.com/weibo?q=" + q + "&Refer=article_weibo";
        if (startTime !=  null && endTime != null) {
            url = url + "&typeall=1&suball=1&timescope=custom:" + startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")) + ":" + endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        }
        if (page != null) {
            url = url + "&page=" + page;
        }
        return url;
    }
}
