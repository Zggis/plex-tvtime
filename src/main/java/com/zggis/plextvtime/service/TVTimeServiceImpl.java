package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.zggis.plextvtime.util.ConsoleColor;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Triplet;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class TVTimeServiceImpl implements TVTimeService {

  @Value("${selenium.driver_location:#{null}}")
  private String driverLocation;

  @Value("${selenium.browser_location:#{null}}")
  private String browserLocation;

  private final Map<String, Triplet<String, String, JSONObject>> userAuth = new HashMap<>();

  @Override
  public void login(String user, String password) {
    if (driverLocation != null) {
      System.setProperty("webdriver.chrome.driver", driverLocation);
    }
    System.setProperty("webdriver.chrome.whitelistedIps", "");
    ChromeOptions options = new ChromeOptions();
    if (browserLocation != null) {
      options.setBinary(browserLocation);
    }
    options.addArguments(
        "--headless", "--no-sandbox", "--disable-dev-shm-usage", "--disable-setuid-sandbox");
    options.addArguments("--remote-allow-origins=*");
    WebDriver driver = new ChromeDriver(options);
    driver.get("https://app.tvtime.com/welcome?mode=auth");
    driver.manage().timeouts().implicitlyWait(Duration.ofMillis(5000));
    String initialJwtToken = null;
    for (int i = 1; i <= 3; i++) {
      try {
        Thread.sleep(5000 + (2000 * i));
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      JavascriptExecutor js = (JavascriptExecutor) driver;
      initialJwtToken =
          (String)
              js.executeScript(
                  String.format("return window.localStorage.getItem('%s');", "flutter.jwtToken"));
      if (StringUtils.hasText(initialJwtToken)) {
        break;
      }
      log.warn(
          "{}Unable to fetch JWT token, trying again...{}", ConsoleColor.YELLOW.value, ConsoleColor.NONE.value);
    }
    if (!StringUtils.hasText(initialJwtToken)) {
      throw new TVTimeException("Unable to fetch JWT token using Selenium, application must exit.");
    }
    driver.close();
    initialJwtToken = initialJwtToken.substring(1, initialJwtToken.length() - 1);
    WebClient client = getWebClient("https://beta-app.tvtime.com");
    WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = client.post();
    WebClient.RequestBodySpec bodySpec = uriSpec.uri("/sidecar?o=https://auth.tvtime.com/v1/login");
    JSONObject credentials = new JSONObject();
    credentials.put("username", user);
    credentials.put("password", password);
    WebClient.RequestHeadersSpec<?> requestHeadersSpec = bodySpec.bodyValue(credentials.toString());
    requestHeadersSpec
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + initialJwtToken)
        .header(
            HttpHeaders.CONTENT_LENGTH, String.valueOf(credentials.toString().getBytes().length))
        .retrieve();
    Mono<String> response = requestHeadersSpec.retrieve().bodyToMono(String.class);
    try {
      JSONObject responsePayload = new JSONObject(response.block());
      Triplet<String, String, JSONObject> jwtTriple =
          new Triplet<>(
              responsePayload.getJSONObject("data").getString("jwt_token"),
              responsePayload.getJSONObject("data").getString("jwt_refresh_token"),
              credentials);
      userAuth.put(user, jwtTriple);
      log.debug(
          "JWT tokens updated for user {} [jwt_token={} jwt_refresh_token={}]",
          user,
          jwtTriple.getValue0(),
          jwtTriple.getValue1());
    } catch (JSONException e) {
      log.error(e.getMessage(), e);
    }
  }

  @Override
  public String watchEpisode(String user, String episodeId) throws TVTimeException {
    if (!isLoggedIn(user)) throw new TVTimeException("You are not logged in");
    JSONObject responsePayload;
    WebClient client = getWebClient("https://app.tvtime.com");
    WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = client.post();
    WebClient.RequestBodySpec bodySpec =
        uriSpec.uri(
            "/sidecar?o=https://api2.tozelabs.com/v2/watched_episodes/episode/"
                + episodeId
                + "&is_rewatch=0");
    WebClient.RequestHeadersSpec<?> requestHeadersSpec =
        bodySpec.bodyValue(userAuth.get(user).getValue2().toString());
    requestHeadersSpec
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAuth.get(user).getValue0())
        .header(
            HttpHeaders.CONTENT_LENGTH,
            String.valueOf(userAuth.get(user).getValue2().toString().getBytes().length))
        .header(HttpHeaders.HOST, "app.tvtime.com:80")
        .retrieve();
    Mono<String> response = requestHeadersSpec.retrieve().bodyToMono(String.class);
    try {
      responsePayload = new JSONObject(response.block());
      return responsePayload.toString();
    } catch (JSONException e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  private boolean isLoggedIn(String user) {
    return userAuth.get(user) != null && StringUtils.hasText(userAuth.get(user).getValue0());
  }

  private WebClient getWebClient(String baseUrl) {
    return WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
        .build();
  }
}
