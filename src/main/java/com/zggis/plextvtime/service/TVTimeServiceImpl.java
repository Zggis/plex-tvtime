package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.IOException;
import java.util.*;

@Component
@Slf4j
public class TVTimeServiceImpl implements TVTimeService {

    @Value("${tvtime.user}")
    private String user;

    @Value("${tvtime.password}")
    private String password;

    private final Map<String, String> cookies = new HashMap<>();

    private final WebClient client;

    private String userId;

    private String avatarImgUrl;

    public TVTimeServiceImpl() {
        client = WebClient.builder()
                .baseUrl("https://www.tvtime.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    @PostConstruct
    public void init() {
        login();
        fetchProfile();
    }

    private void fetchProfile() throws TVTimeException {
        if (!isLoggedIn())
            throw new TVTimeException("You are not logged in");
        Document doc;
        try {
            doc = Jsoup.connect("https://www.tvtime.com/en")
                    .cookie("tvstRemember", cookies.get("tvstRemember"))
                    .cookie("symfony", cookies.get("symfony"))
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept-Language", "*")
                    .get();
            userId = doc.selectFirst("li.profile")
                    .selectFirst("a").attr("href")
                    .replace("/en/user/", "").replace("/profile", "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<Show> getShows() throws TVTimeException {
        if (!isLoggedIn())
            throw new TVTimeException("You are not logged in");
        Document doc;
        Set<Show> shows = new HashSet<>();
        try {
            doc = Jsoup.connect("https://www.tvtime.com/en/user/" + userId + "/profile")
                    .cookie("tvstRemember", cookies.get("tvstRemember"))
                    .cookie("symfony", cookies.get("symfony"))
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept-Language", "*")
                    .get();
            avatarImgUrl = doc.selectFirst("div.profile-nav").selectFirst("img").attr("src");
            Elements items = doc.select("div.show");
            for (Element element : items) {
                shows.add(new Show(
                        element.selectFirst("img").attr("alt").trim(),
                        element.selectFirst("a").attr("href").replace("/en/show/", ""),
                        element.selectFirst("img").attr("src")
                ));
            }
            log.info("Fetched {} shows", shows.size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return shows;
    }

    @Override
    public Set<Episode> getEpisodes(String tvdbId) throws TVTimeException {
        if (!isLoggedIn())
            throw new TVTimeException("You are not logged in");
        Document doc;
        Set<Episode> episodes = new HashSet<>();
        try {
            doc = Jsoup.connect("https://www.tvtime.com/en/show/" + tvdbId)
                    .cookie("tvstRemember", cookies.get("tvstRemember"))
                    .cookie("symfony", cookies.get("symfony"))
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept-Language", "*")
                    .get();

            Elements items = doc.select("li.episode-wrapper");

            for (Element element : items) {
                episodes.add(new Episode(
                        element.selectFirst("a").attr("href").replace("/en/show/" + tvdbId + "/episode/", ""),
                        element.selectFirst("span.episode-name").text().trim(),
                        element.selectFirst("span.episode-air-date").text().trim()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return episodes;
    }

    @Override
    public String watchEpisode(String episodeId) throws TVTimeException {
        if (!isLoggedIn())
            throw new TVTimeException("You are not logged in");
        WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = client.put();
        WebClient.RequestBodySpec bodySpec = uriSpec.uri("/watched_episodes")
                .cookie("tvstRemember", cookies.get("tvstRemember"))
                .cookie("symfony", cookies.get("symfony"))
                .cookie("user_id", userId)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json, text/javascript, */*; q=0.01");
        LinkedMultiValueMap<String, Integer> map = new LinkedMultiValueMap<>();
        map.add("episode_id", Integer.valueOf(episodeId));
        WebClient.RequestHeadersSpec<?> headersSpec = bodySpec.bodyValue("episode_id=" + episodeId);


        Mono<Tuple2> mono = headersSpec.exchangeToMono(response ->
                response.bodyToMono(String.class)
                        .map(stringBody -> Tuples.of(stringBody, response.cookies())

                        )
        );
        Tuple2<String, MultiValueMap<String, String>> payload = mono.block();
        if (payload != null) {
            updateCookies(payload.getT2());
            return payload.getT1();
        }
        return null;
    }

    private void updateCookies(MultiValueMap<String, String> newCookies) throws TVTimeException {
        if (!CollectionUtils.isEmpty(newCookies)) {
            newCookies.forEach((k, v) ->
                    {
                        if ("tvstRemember".equals(k) || "symfony".equals(k))
                            this.cookies.put(k, parseParam(v.toString()));
                    }
            );
            log.debug("Cookies updated : [tvstRemember={};symfony={}]", this.cookies.get("tvstRemember"), this.cookies.get("symfony"));
            if (!StringUtils.hasText(cookies.get("tvstRemember")) || "deleted".equals(cookies.get("tvstRemember"))) {
                throw new TVTimeException("Session has expired, you must login again");
            }
        }
    }

    private boolean isLoggedIn() {
        if (!StringUtils.hasText(cookies.get("tvstRemember")) || "deleted".equals(cookies.get("tvstRemember")) || !StringUtils.hasText(cookies.get("symfony"))) {
            return false;
        }
        return true;
    }

    @Override
    public void login() {
        WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = client.post();
        WebClient.RequestBodySpec bodySpec = uriSpec.uri("/signin");
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("username", user);
        map.add("password", password);
        WebClient.RequestHeadersSpec<?> headersSpec = bodySpec.body(
                BodyInserters.fromMultipartData(map));
        Mono<MultiValueMap> mono = headersSpec.exchangeToMono(response ->
                response.bodyToMono(String.class)
                        .map(stringBody ->
                                response.cookies()
                        )
        );
        this.cookies.clear();
        MultiValueMap<String, String> payloadCookies = mono.block();
        if (payloadCookies != null) {
            payloadCookies.forEach((k, v) ->
                    {
                        if ("tvstRemember".equals(k) || "symfony".equals(k))
                            this.cookies.put(k, parseParam(v.toString()));
                    }
            );
            if (!this.cookies.containsKey("tvstRemember") || !this.cookies.containsKey("symfony")) {
                throw new TVTimeException("Your TV Time credentials are invalid");
            }
            log.info("Login Successful");
        }
    }

    private String parseParam(String v) {
        if (StringUtils.hasText(v)) {
            String param = v.split(" ")[0];
            return param.substring(param.indexOf('=') + 1, param.indexOf(';'));
        }
        return null;
    }

    @Override
    public String getAvatarImgUrl() {
        return avatarImgUrl;
    }

    @Override
    public String getUserId() {
        return userId;
    }
}
