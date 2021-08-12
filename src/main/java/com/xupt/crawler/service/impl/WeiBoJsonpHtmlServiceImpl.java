package com.xupt.crawler.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.xupt.crawler.controller.resp.WeiboDomain;
import com.xupt.crawler.service.CrawlerService;
import com.xupt.crawler.service.WeiBoCookieService;
import com.xupt.crawler.service.WeiBoJsonpHtmlService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WeiBoJsonpHtmlServiceImpl implements WeiBoJsonpHtmlService {

    @Autowired
    private WeiBoCookieService weiBoCookieService;

    @Autowired
    private CrawlerService crawlerService;

    private final Executor parseExecutor = Executors.newWorkStealingPool(4);

    private final ExecutorCompletionService<WeiboDomain> parseCompleteService = new ExecutorCompletionService<>(parseExecutor);

    private final Executor relayExecutor = Executors.newWorkStealingPool(4);

    private final ExecutorCompletionService<List<RelayParseData>> relayCompleteService = new ExecutorCompletionService<>(relayExecutor);

    @Override
    public int parsePageCount(String html) {
        Document doc = Jsoup.parse(html);
        Element page = doc.getElementsByClass("m-page").first();
        Element curs = page.getElementsByClass("s-scroll").first();
        String pageCountStr = curs.child(curs.childrenSize() - 1).child(0).text();
        return Integer.parseInt(pageCountStr.substring(1, pageCountStr.indexOf("页")));
    }

    @Override
    public int parsePageSize(String html) {
        Document doc = Jsoup.parse(html);
        Elements cardWraps = doc.getElementsByClass("card-wrap");
        int pageSize = 0;
        for (Element element : cardWraps) {
            if (element.getElementsByClass("name").isEmpty()) {
                continue;
            }
            pageSize++;
        }
        return pageSize;
    }

    @Override
    public List<WeiboDomain> parseData(String html, String realCookie) {
        List<WeiboDomain> weiboDomains = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements cardWraps = doc.getElementsByClass("card-wrap");
        for (Element element : cardWraps) {
            parseCompleteService.submit(() -> parseSingleData(element, realCookie));
//            WeiboDomain weiboDomain = parseSingleData(element);
//            if (weiboDomain != null) {
//                weiboDomains.add(weiboDomain);
//            }
        }
        for (int i = 0; i < cardWraps.size(); i++) {
            try {
                WeiboDomain weiboDomain = parseCompleteService.take().get();
                if (weiboDomain != null) {
                    weiboDomains.add(weiboDomain);
                }
            } catch (Exception e) {
                log.error("get single data exception. error is ", e);
            }
        }
        return weiboDomains;
    }

    private WeiboDomain parseSingleData(Element element, String realCookie) {
        if (element.getElementsByClass("name").isEmpty()) {
            return null;
        }
        Element user = element.getElementsByClass("name").first();
        String name = element.getElementsByClass("name").first().attr("nick-name");
        if (Strings.isEmpty(name)) {
            return null;
        }
        String uid = user.attr("href").replace("//", "").split("/")[1];
        uid = uid.substring(0, uid.indexOf("?"));

        String homepageLink = "https://weibo.com/u/" + uid;
        String homepageJsonStr = crawlerService.getHtml(homepageLink, realCookie);
        if (uid.equals("3108585423")) {
            System.out.println("knjdnfjnjnf" + uid + "djjfjfjn" + homepageJsonStr);
        }
        String fans = Strings.EMPTY;
        if (homepageJsonStr.contains("粉丝")) {
            int index = homepageJsonStr.indexOf("粉丝");
            fans = homepageJsonStr.substring(index - 53, index - 33);
            for (int i = fans.length() - 1; i >= 0; i--) {
                String numStr = fans.charAt(i) + "";
                try {
                    Integer.parseInt(numStr);
                } catch (Exception e) {
                    fans = fans.substring(i + 1);
                    break;
                }
            }
        }
        String address = Strings.EMPTY;
        if (homepageJsonStr.contains("<span class=\\\"item_text W_fl\\\">")) {
            int index = homepageJsonStr.indexOf("<span class=\\\"item_text W_fl\\\">");
            address = homepageJsonStr.substring(index + 31, index + 101)
                    .replaceAll("\\\\r", "").replaceAll("\\\\n", "").replaceAll("\\\\t", "").replaceAll(" ", "");
        }

        String txt = getLabelHtml(element, "txt");
        String img = Strings.EMPTY;
        if (element.getElementsByClass("m1 w1 c1").size() > 0) {
            img = element.getElementsByClass("m1 w1 c1").first().child(0).child(0).attr("src").replace("//", "");
        }
        String date = Strings.EMPTY;
        if (element.getElementsByClass("from").size() > 0) {
            date = element.getElementsByClass("from").first().text();
        }
        String relayNum = Strings.EMPTY;
        String comment = Strings.EMPTY;
        String likes = Strings.EMPTY;
        if (element.getElementsByClass("card-act").size() > 0) {
            Elements cardAct_url_lis = element.getElementsByClass("card-act").first().child(0).getElementsByTag("li");
            relayNum = cardAct_url_lis.get(1).child(0).text();
            comment = cardAct_url_lis.get(2).child(0).text();
            likes = cardAct_url_lis.get(3).child(0).text();
        }

        //获取转发相关信息
        String cookie = weiBoCookieService.getCookie();
        List<String> allRelayUidList = new ArrayList<>();
        List<String> allRelayNames = new ArrayList<>();
        String relayKey = element.attr("mid");
        String relayLink = "https://weibo.com/aj/v6/mblog/info/big?ajwvr=6&id=" + relayKey + "&page=1";
        String relayJsonStr;
        try {
            relayJsonStr = crawlerService.getHtml(relayLink, cookie);
        } catch (Exception e) {
//            cookie = "_s_tentry=link.csdn.net; Apache=7478629912745.258.1627205657412; SINAGLOBAL=7478629912745.258.1627205657412; ULV=1627205657422:1:1:1:7478629912745.258.1627205657412:; login_sid_t=66f5d667c5fc5cd5d8fcfaf87e7ba3d6; cross_origin_proto=SSL; SUB=_2A25MC9TiDeRhGeBK41AU9i7EwjyIHXVvYUEqrDV8PUNbmtAKLVLbkW9NR2ITSFnAJF4pS2SJDDjX-HCYeChZRdXZ; SUBP=0033WrSXqPxfM725Ws9jqgMF55529P9D9WWJKsUxaFmh3odVv9DisjkQ5JpX5KzhUgL.FoqX1hzfSo5R1K52dJLoI7DsIPiLeK.Reh5N; ALF=1659951153; SSOLoginState=1628415154; wvr=6; UOR=link.csdn.net,s.weibo.com,www.baidu.com; webim_unReadCount=%7B%22time%22%3A1628418522452%2C%22dm_pub_total%22%3A0%2C%22chat_group_client%22%3A0%2C%22chat_group_notice%22%3A0%2C%22allcountNum%22%3A1%2C%22msgbox%22%3A0%7D";
            cookie = weiBoCookieService.getCookie();
            relayJsonStr = crawlerService.getHtml(relayLink, cookie);
        }
        RelayEntity relayEntity = JSONObject.parseObject(relayJsonStr, RelayEntity.class);
        //第一页
        List<RelayParseData> relay = parseRelay(relayEntity.getData().getHtml());
        allRelayUidList.addAll(relay.stream().map(RelayParseData::getUid).collect(Collectors.toList()));
        allRelayNames.addAll(relay.stream().map(RelayParseData::getName).collect(Collectors.toList()));
        //其他页
        int totalPage = relayEntity.getData().getPage().getTotalpage();
        for (int i = 2; i <= totalPage; i++) {
            String pageRelayLink = "https://weibo.com/aj/v6/mblog/info/big?ajwvr=6&id=" + relayKey + "&page=" + i;
            String finalCookie = cookie;
            relayCompleteService.submit(() -> getRelay(pageRelayLink, finalCookie));
//            relayNames = getRelay(pageRelayLink, cookie);
//            allRelayNames.addAll(relayNames);
        }
        for (int i = 2; i <= totalPage; i++) {
            try {
                relay = relayCompleteService.take().get();
                allRelayUidList.addAll(relay.stream().map(RelayParseData::getUid).collect(Collectors.toList()));
                allRelayNames.addAll(relay.stream().map(RelayParseData::getName).collect(Collectors.toList()));
            } catch (Exception e) {
                log.error("get relay exception. error is ", e);
            }
        }

        WeiboDomain weiboDomain = new WeiboDomain();
        weiboDomain.setUid(uid);
        weiboDomain.setName(name);
        weiboDomain.setFans(fans);
        weiboDomain.setAddress(address);
        weiboDomain.setTxt(txt);
        weiboDomain.setImg(img);
        weiboDomain.setDate(date);
        weiboDomain.setRelayNum(relayNum);
        weiboDomain.setComment(comment);
        weiboDomain.setLikes(likes);
        weiboDomain.setRelayUidList(JSONObject.toJSONString(allRelayUidList));
        weiboDomain.setRelayNames(JSONObject.toJSONString(allRelayNames));
        return weiboDomain;
    }

    private String getLabelHtml(Element element, String className) {
        Elements elements = element.getElementsByClass(className);
        if (elements.size() > 0) {
            return elements.first().text();
        }
        return Strings.EMPTY;
    }

    private List<RelayParseData> getRelay(String relayLink, String cookie) {
        String relayJsonStr;
        try {
            relayJsonStr = crawlerService.getHtml(relayLink, cookie);
        } catch (Exception e) {
//            cookie = "_s_tentry=link.csdn.net; Apache=7478629912745.258.1627205657412; SINAGLOBAL=7478629912745.258.1627205657412; ULV=1627205657422:1:1:1:7478629912745.258.1627205657412:; login_sid_t=66f5d667c5fc5cd5d8fcfaf87e7ba3d6; cross_origin_proto=SSL; SUB=_2A25MC9TiDeRhGeBK41AU9i7EwjyIHXVvYUEqrDV8PUNbmtAKLVLbkW9NR2ITSFnAJF4pS2SJDDjX-HCYeChZRdXZ; SUBP=0033WrSXqPxfM725Ws9jqgMF55529P9D9WWJKsUxaFmh3odVv9DisjkQ5JpX5KzhUgL.FoqX1hzfSo5R1K52dJLoI7DsIPiLeK.Reh5N; ALF=1659951153; SSOLoginState=1628415154; wvr=6; UOR=link.csdn.net,s.weibo.com,www.baidu.com; webim_unReadCount=%7B%22time%22%3A1628418522452%2C%22dm_pub_total%22%3A0%2C%22chat_group_client%22%3A0%2C%22chat_group_notice%22%3A0%2C%22allcountNum%22%3A1%2C%22msgbox%22%3A0%7D";
            cookie = weiBoCookieService.getCookie();
            relayJsonStr = crawlerService.getHtml(relayLink, cookie);
        }
        RelayEntity relayEntity = JSONObject.parseObject(relayJsonStr, RelayEntity.class);
        return parseRelay(relayEntity.getData().getHtml());
    }

    private List<RelayParseData> parseRelay(String html) {
        List<RelayParseData> relays = new ArrayList<>();

        Document relayDoc = Jsoup.parse(html);
        Elements relayElements = relayDoc.getElementsByClass("list_li S_line1 clearfix");
        for (Element element : relayElements) {
            Element relayUser = element.getElementsByClass("WB_face W_fl").first().child(0).child(0);
            String relayUid = relayUser.attr("usercard").replaceAll("id=", "");
            String relayName = relayUser.attr("alt");
            RelayParseData relayParseData = new RelayParseData(relayUid, relayName);
            relays.add(relayParseData);
        }
        return relays;
    }

    @Data
    private static class RelayEntity {

        private String code;

        private Data data;

        private String msg;

        @lombok.Data
        private static class Data {

            private Integer count;

            private String html;

            private Page page;

            @lombok.Data
            private static class Page {

                private Integer pagenum;

                private Integer totalpage;
            }
        }
    }

    @Data
    @AllArgsConstructor
    private static class RelayParseData {

        private String uid;

        private String name;
    }
}
