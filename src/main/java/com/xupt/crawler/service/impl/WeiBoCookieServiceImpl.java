package com.xupt.crawler.service.impl;

import com.xupt.crawler.service.CrawlerService;
import com.xupt.crawler.service.WeiBoCookieService;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WeiBoCookieServiceImpl implements WeiBoCookieService {

    @Autowired
    private CrawlerService crawlerService;

    @Override
    public String getCookie() {
        String[] tidAndC = getTidAndC();
        String t = tidAndC[0];
        String w = tidAndC[1];
        JSONObject obj;
        for(;;) {
            String url = "https://passport.weibo.com/visitor/visitor?a=incarnate&t=" + t + "&w=" + w + "&c=0" + "&gc=&cb=cross_domain&from=weibo&_rand=" + Math.random();
            String body = crawlerService.getHtml(url, Strings.EMPTY, true);
            body = body.replaceAll("window.cross_domain && cross_domain\\(", "");
            body = body.replaceAll("\\);", "");
            obj = JSONObject.fromObject(body).getJSONObject("data");
            if (obj != null && obj.has("sub") && obj.has("subp")) {
                break;
            }
            tidAndC = getTidAndC();
            t = tidAndC[0];
            w = tidAndC[1];
        }
//        String cookie = "YF-Page-G0=" + getYF() + "; SUB=" + obj.getString("sub") + "; SUBP=" + obj.getString("subp");
        String cookie = "SUB=" + obj.getString("sub") + "; SUBP=" + obj.getString("subp");
        log.info("cookie: {}", cookie);
        return cookie;
    }

    private String[] getTidAndC() {
        String url = "https://passport.weibo.com/visitor/genvisitor?cb=gen_callback";
        String body = crawlerService.getHtml(url, Strings.EMPTY, true);
        body = body.replaceAll("window.gen_callback && gen_callback\\(", "");
        body = body.replaceAll("\\);", "");
        JSONObject json = JSONObject.fromObject(body).getJSONObject("data");
        String t = "";
        String w = "";

        String c = json.containsKey("confidence") ? json.getString("confidence") : "100";
        if (json.containsKey("new_tid")) {
            w = json.getBoolean("new_tid") ? "3" : "2";
        }
        if (json.containsKey("tid")) {
            t = json.getString("tid");
        }
        return new String[]{t, w};
    }

    public String getYF() {
//        String domain = "1087030002_2975_5012_0";
//        String url = "https://d.weibo.com/" + domain;
//        String jsonStr = crawlerService.getHtml(url, Strings.EMPTY);
//
//        List<Cookie> cookies = cookieStore.getCookies();
//        String str = "";
//        for (Cookie cookie : cookies)
//        {
//            str = cookie.getValue();
//        }
//        return str;
        return "SINAGLOBAL=9020698669238.129.1626538444886; login_sid_t=15adec5e59fad21fe4d5ae1c83ebbe0c; cross_origin_proto=SSL; wb_view_log=1920*10801; _s_tentry=www.baidu.com; Apache=4525079695513.794.1628417220481; ULV=1628417220484:2:1:1:4525079695513.794.1628417220481:1626538444891; wb_view_log_7548501078=1920*10801; UOR=,,login.sina.com.cn; ";
    }
}
