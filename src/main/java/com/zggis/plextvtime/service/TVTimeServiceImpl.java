package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

    private static final String TVST_REMEMBER = "tvstRemember";
    private static final String SYMFONY = "symfony";
    private static final String DELETED = "deleted";

    private final Map<String, Map<String, String>> userCookies = new HashMap<>();
    private final WebClient client;

    private final Map<String, String> userIdMap = new HashMap<>();

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

    }

    @Override
    public void login(String user, String password) {
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
        MultiValueMap<String, String> payloadCookies = mono.block();
        if (payloadCookies != null) {
            Map<String, String> cookies = new HashMap<>();
            payloadCookies.forEach((k, v) ->
                    {
                        if (TVST_REMEMBER.equals(k) || SYMFONY.equals(k)) {
                            cookies.put(k, parseCookieValue(k, v.toString()));
                        }
                    }
            );
            this.userCookies.put(user, cookies);
            if (!this.userCookies.get(user).containsKey(TVST_REMEMBER) || !this.userCookies.get(user).containsKey(SYMFONY)) {
                throw new TVTimeException("TV Time credentials for " + user + " are invalid");
            }
            log.debug("Cookies updated for user {} [tvstRemember={} symfony={}]", user, userCookies.get(user).get(TVST_REMEMBER), userCookies.get(user).get(SYMFONY));
        }
    }

    @Override
    public void fetchProfile(String user) throws TVTimeException, IOException {
        if (!isLoggedIn(user))
            throw new TVTimeException("You are not logged in");
        Document doc;
        doc = Jsoup.connect("https://www.tvtime.com/en")
                .cookie(TVST_REMEMBER, userCookies.get(user).get(TVST_REMEMBER))
                .cookie(SYMFONY, userCookies.get(user).get(SYMFONY))
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept-Language", "*")
                .get();
        userIdMap.put(user, doc.selectFirst("li.profile")
                .selectFirst("a").attr("href")
                .replace("/en/user/", "").replace("/profile", ""));

    }

    @Override
    public String watchEpisode(String user, String episodeId) throws TVTimeException {
        if (!isLoggedIn(user))
            throw new TVTimeException("You are not logged in");
        WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = client.put();
        WebClient.RequestBodySpec bodySpec = uriSpec.uri("/watched_episodes")
                .cookie(TVST_REMEMBER, userCookies.get(user).get(TVST_REMEMBER))
                .cookie(SYMFONY, userCookies.get(user).get(SYMFONY))
                .cookie("user_id", userIdMap.get(user))
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
            updateCookies(user, payload.getT2());
            return payload.getT1();
        }
        return null;
    }

    private void updateCookies(String user, MultiValueMap<String, String> newCookies) throws TVTimeException {
        if (!CollectionUtils.isEmpty(newCookies)) {
            newCookies.forEach((k, v) ->
                    {
                        if (TVST_REMEMBER.equals(k) || SYMFONY.equals(k))
                            this.userCookies.get(user).put(k, parseCookieValue(k, v.toString()));
                    }
            );
            log.trace("Cookies updated for {}", user);
            if (!StringUtils.hasText(userCookies.get(user).get(TVST_REMEMBER)) || DELETED.equals(userCookies.get(user).get(TVST_REMEMBER))) {
                throw new TVTimeException("Session has expired, you must login again");
            }
        }
    }

    private boolean isLoggedIn(String user) {
        return StringUtils.hasText(userCookies.get(user).get(TVST_REMEMBER)) && !DELETED.equals(userCookies.get(user).get(TVST_REMEMBER)) && StringUtils.hasText(userCookies.get(user).get(SYMFONY));
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
