package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;
import com.zggis.plextvtime.util.ThreadUtil;
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
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class TVTimeServiceImpl implements TVTimeService {

    private static final String TVST_REMEMBER = "tvstRemember";
    private static final String SYMFONY = "symfony";
    private static final String DELETED = "deleted";
    private final Map<String, String> cookies = new HashMap<>();
    private final WebClient client;
    @Value("${tvtime.user}")
    private String user;
    @Value("${tvtime.password}")
    private String password;
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
        for (int i = 1; i <= 5; i++) {
            try {
                login();
                fetchProfile();
                return;
            } catch (TVTimeException e) {
                log.error(e.getMessage());
                System.exit(1);
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                log.warn("Connection to TV Time failed, will retry in {}s, attempts remaining {}", (3000 * i) / 1000, 5 - i);
                ThreadUtil.delay(3000 * i);
            }
        }
        throw new TVTimeException("Unable to connect to TVTime after multiple attempts, please check your internet connection. It is possible http://tvtime.com is unavailable.");
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
                        if (TVST_REMEMBER.equals(k) || SYMFONY.equals(k))
                            this.cookies.put(k, parseCookieValue(k, v.toString()));
                    }
            );
            if (!this.cookies.containsKey(TVST_REMEMBER) || !this.cookies.containsKey(SYMFONY)) {
                throw new TVTimeException("Your TV Time credentials are invalid");
            }
            log.debug("Cookies updated [tvstRemember={} symfony={}]", cookies.get(TVST_REMEMBER), cookies.get(SYMFONY));
        }
    }

    private void fetchProfile() throws TVTimeException, IOException {
        if (!isLoggedIn())
            throw new TVTimeException("You are not logged in");
        Document doc;
        doc = Jsoup.connect("https://www.tvtime.com/en")
                .cookie(TVST_REMEMBER, cookies.get(TVST_REMEMBER))
                .cookie(SYMFONY, cookies.get(SYMFONY))
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept-Language", "*")
                .get();
        userId = doc.selectFirst("li.profile")
                .selectFirst("a").attr("href")
                .replace("/en/user/", "").replace("/profile", "");

    }

    @Override
    public String watchEpisode(String episodeId) throws TVTimeException {
        if (!isLoggedIn())
            throw new TVTimeException("You are not logged in");
        WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = client.put();
        WebClient.RequestBodySpec bodySpec = uriSpec.uri("/watched_episodes")
                .cookie(TVST_REMEMBER, cookies.get(TVST_REMEMBER))
                .cookie(SYMFONY, cookies.get(SYMFONY))
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
                        if (TVST_REMEMBER.equals(k) || SYMFONY.equals(k))
                            this.cookies.put(k, parseCookieValue(k, v.toString()));
                    }
            );
            log.debug("Cookies updated : [tvstRemember={} symfony={}]", this.cookies.get(TVST_REMEMBER), this.cookies.get(SYMFONY));
            if (!StringUtils.hasText(cookies.get(TVST_REMEMBER)) || DELETED.equals(cookies.get(TVST_REMEMBER))) {
                throw new TVTimeException("Session has expired, you must login again");
            }
        }
    }

    private boolean isLoggedIn() {
        return StringUtils.hasText(cookies.get(TVST_REMEMBER)) && !DELETED.equals(cookies.get(TVST_REMEMBER)) && StringUtils.hasText(cookies.get(SYMFONY));
    }

    private String parseCookieValue(String k, String v) {
        if (StringUtils.hasText(v)) {
            String[] params = v.split(" ");
            for (String param : params) {
                if (param.contains(k) && !param.contains(DELETED)) {
                    return param.substring(param.indexOf('=') + 1, param.indexOf(';'));
                }
            }
        }
        return null;
    }
}
