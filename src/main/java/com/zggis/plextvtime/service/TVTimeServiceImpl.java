package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import java.util.HashMap;
import java.util.Map;

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
                            this.cookies.put(k, parseCookieValue(k, v.toString()));
                    }
            );
            log.debug("Cookies updated : [tvstRemember={} symfony={}]", this.cookies.get("tvstRemember"), this.cookies.get("symfony"));
            if (!StringUtils.hasText(cookies.get("tvstRemember")) || "deleted".equals(cookies.get("tvstRemember"))) {
                throw new TVTimeException("Session has expired, you must login again");
            }
        }
    }

    private boolean isLoggedIn() {
        return StringUtils.hasText(cookies.get("tvstRemember")) && !"deleted".equals(cookies.get("tvstRemember")) && StringUtils.hasText(cookies.get("symfony"));
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
                            this.cookies.put(k, parseCookieValue(k, v.toString()));
                    }
            );
            if (!this.cookies.containsKey("tvstRemember") || !this.cookies.containsKey("symfony")) {
                throw new TVTimeException("Your TV Time credentials are invalid");
            }
            log.debug("Cookies updated [tvstRemember={} symfony={}]", cookies.get("tvstRemember"), cookies.get("symfony"));
            log.info("Login Successful");
        }
    }

    private String parseCookieValue(String k, String v) {
        if (StringUtils.hasText(v)) {
            String[] params = v.split(" ");
            for (String param : params) {
                if (param.contains(k) && !param.contains("deleted")) {
                    return param.substring(param.indexOf('=') + 1, param.indexOf(';'));
                }
            }
        }
        return null;
    }

    @Override
    public String getUserId() {
        return userId;
    }
}
