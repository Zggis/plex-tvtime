package com.zggis.plextvtime.service;

import com.zggis.plextvtime.config.AccountConfig;
import com.zggis.plextvtime.config.AccountLink;
import com.zggis.plextvtime.dto.plex.Guid;
import com.zggis.plextvtime.dto.plex.PlexWebhook;
import com.zggis.plextvtime.exception.TVTimeException;
import com.zggis.plextvtime.util.ConsoleColor;
import com.zggis.plextvtime.util.ThreadUtil;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@Slf4j
public class MediaManagerServiceImpl implements MediaManagerService {

  @Autowired private AccountConfig accountConfig;

  @Autowired private TVTimeService tvTimeService;

  @Value("${track-movies:false}")
  private boolean trackMovies;

  private final Map<String, Set<String>> plexUsersMap = new HashMap<>();

  private final Map<String, Set<Show>> excludedShowsMap = new HashMap<>();

  private final Map<String, Set<Show>> includedShowsMap = new HashMap<>();

  private final BlockingQueue<PlexWebhook> queue = new LinkedBlockingQueue<>();

  private final Map<String, AccountLink> tvtimeAccountMap = new HashMap<>();

  @PostConstruct
  public void init() {
    for (AccountLink account : accountConfig.getAccounts()) {
      plexUsersMap.put(account.getTvtimeUser(), new HashSet<>());
      excludedShowsMap.put(account.getTvtimeUser(), new HashSet<>());
      includedShowsMap.put(account.getTvtimeUser(), new HashSet<>());
      if (StringUtils.hasText(account.getPlexUsers())) {
        for (String user : account.getPlexUsers().split(",")) {
          plexUsersMap.get(account.getTvtimeUser()).add(user.toLowerCase());
        }
      } else {
        log.warn(
            "{}TVTime user {} has no configured Plex users, no events will be processed for them{}",
            ConsoleColor.YELLOW.value,
            account.getTvtimeUser(),
            ConsoleColor.NONE.value);
      }
      if (StringUtils.hasText(account.getPlexShowsExclude())) {
        for (String show : account.getPlexShowsExclude().split(",")) {
          excludedShowsMap
              .get(account.getTvtimeUser())
              .add(new Show(StringUtils.replace(show, "%2C", ",").trim()));
        }
      }
      if (StringUtils.hasText(account.getPlexShowsInclude())) {
        for (String show : account.getPlexShowsInclude().split(",")) {
          includedShowsMap
              .get(account.getTvtimeUser())
              .add(new Show(StringUtils.replace(show, "%2C", ",").trim()));
        }
        if (StringUtils.hasText(account.getPlexShowsExclude())) {
          log.warn(
              "{}Included shows is set for user {}, but will be ignored since excluded shows is also set{}",
              ConsoleColor.YELLOW.value,
              account.getTvtimeUser(),
              ConsoleColor.NONE.value);
        }
      }
    }
    for (AccountLink account : accountConfig.getAccounts()) {
      loginUser(account.getTvtimeUser(), account.getTvtimePassword());
      tvtimeAccountMap.put(account.getTvtimeUser(), account);
    }
    Thread t1 = new Thread(new WebhookProcessor());
    t1.setName("queue-exec");
    t1.start();
  }

  private class WebhookProcessor implements Runnable {

    public void run() {
      while (true) {
        try {
          processWebhook(queue.take());
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        } catch (TVTimeException e) {
          log.error("Unable to authenticate with TVTime, please check your credentials");
          log.error(e.getMessage(), e);
          System.exit(1);
        } catch (Exception e) {
          log.warn(
              "{}Unable to process webhook message: {}{}",
              ConsoleColor.YELLOW.value,
              e.getMessage(),
              ConsoleColor.NONE.value);
        }
      }
    }
  }

  @Override
  public void markAsWatched(PlexWebhook webhook) {
    synchronized (queue) {
      try {
        queue.put(webhook);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void processWebhook(PlexWebhook webhook) throws TVTimeException {
    String mediaType = webhook.metadata.librarySectionType;
    if (mediaType == null
        || mediaType.isEmpty()
        || !mediaType.equals("show") && !mediaType.equals("movie")) {
      log.warn(
          "{}Ignoring webhook for library type '{}', only type show and movie will be processed{}",
          ConsoleColor.YELLOW.value,
          mediaType,
          ConsoleColor.NONE.value);
      return;
    }
    if (!webhook.event.equals("media.scrobble")) {
      log.info(
          "Ignoring webhook for event type '{}', only type media.scrobble will be processed",
          webhook.event);
      return;
    }
    String mediaId = null;

    for (Guid guid : webhook.metadata.guid) {
      if (guid.id.contains("tvdb")) {
        mediaId = guid.id.replace("tvdb://", "");
      }
    }
    if (StringUtils.hasText(mediaId)) {
      if (mediaType.equals("show")) {
        log.info(
            "{}Processing webhook for {} S{}E{} - {}{}",
            ConsoleColor.CYAN.value,
            webhook.metadata.grandparentTitle,
            webhook.metadata.parentIndex,
            webhook.metadata.index,
            webhook.metadata.title,
            ConsoleColor.NONE.value);
      } else if (trackMovies) {
        log.info(
            "{}Processing webhook for {} ({}){}",
            ConsoleColor.CYAN.value,
            webhook.metadata.title,
            webhook.metadata.year,
            ConsoleColor.NONE.value);
      } else {
        log.info(
            "{}Ignoring webhook for movie {} ({}), to enable movie tracking set TRACK_MOVIES to true{}",
            ConsoleColor.YELLOW.value,
            webhook.metadata.title,
            webhook.metadata.year,
            ConsoleColor.NONE.value);
        return;
      }
      for (AccountLink account : accountConfig.getAccounts()) {
        log.debug("Checking TVTime account {}...", account.getTvtimeUser());
        if (hasPlexUser(account, webhook.account.title)) {
          sendUserWatchRequest(account.getTvtimeUser(), mediaId, mediaType, webhook);
        } else {
          log.info(
              "Ignoring webhook from plex user '{}', they are not linked to {}",
              webhook.account.title,
              account.getTvtimeUser());
        }
      }
    } else {
      log.warn(
          "{}Cannot process webhook because it does not have an TVDB episode id associated with it{}",
          ConsoleColor.YELLOW.value,
          ConsoleColor.NONE.value);
    }
  }

  private void sendUserWatchRequest(
      String tvtimeUser, String mediaId, String mediaType, PlexWebhook webhook) {
    if (mediaType.equals("show")) {
      if (!excludedShowsMap.get(tvtimeUser).isEmpty()) {
        if (excludedShowsMap
            .get(tvtimeUser)
            .contains(new Show(webhook.metadata.grandparentTitle))) {
          log.info(
              "Ignoring webhook for show '{}', its in the excluded list for {}",
              webhook.metadata.grandparentTitle,
              tvtimeUser);
          return;
        }
      } else if (!includedShowsMap.get(tvtimeUser).isEmpty()) {
        if (!includedShowsMap
            .get(tvtimeUser)
            .contains(new Show(webhook.metadata.grandparentTitle))) {
          log.info(
              "Ignoring webhook for show '{}', its not in the included list for {}",
              webhook.metadata.grandparentTitle,
              tvtimeUser);
          return;
        }
      }
    }
    boolean success = false;
    for (int i = 1; i <= 5; i++) {
      try {
        log.debug(tvTimeService.watchMedia(tvtimeUser, mediaId, mediaType));
        if (mediaType.equals("show")) {
          log.info(
              "{}{} S{}E{} - {}, was successfully marked as watched for {}!{}",
              ConsoleColor.GREEN.value,
              webhook.metadata.grandparentTitle,
              webhook.metadata.parentIndex,
              webhook.metadata.index,
              webhook.metadata.title,
              tvtimeUser,
              ConsoleColor.NONE.value);
        } else {
          log.info(
              "{}{} ({}), was successfully marked as watched for {}!{}",
              ConsoleColor.GREEN.value,
              webhook.metadata.title,
              webhook.metadata.year,
              tvtimeUser,
              ConsoleColor.NONE.value);
        }
        success = true;
        break;
      } catch (WebClientResponseException e) {
        if (e.getStatusCode().value() == 401) {
          log.warn(
              "{}TVTime JWT token has expired, will attempt to renew and try again.{}",
              ConsoleColor.YELLOW.value,
              ConsoleColor.NONE.value);
          throw new TVTimeException(
              e.getMessage() + " Response body: " + e.getResponseBodyAsString());
        }
      } catch (TVTimeException e) {
        log.warn(
            "{}Connection to TV Time failed for user {}, will retry in {}s, attempts remaining {}{}",
            ConsoleColor.YELLOW.value,
            tvtimeUser,
            (6000 * i) / 1000,
            5 - i,
            ConsoleColor.NONE.value);
        ThreadUtil.delay(3000 * i);
        AccountLink tvtimeAccount = tvtimeAccountMap.get(tvtimeUser);
        tvTimeService.login(tvtimeAccount.getTvtimeUser(), tvtimeAccount.getTvtimePassword());
        ThreadUtil.delay(3000 * i);
      } catch (WebClientRequestException e) {
        log.error(
            "{}Unable to reach https://tvtime.com, please check your internet connection, will retry in 2 minutes.{}",
            ConsoleColor.YELLOW.value,
            ConsoleColor.NONE.value);
        log.debug(e.getMessage(), e);
        i--;
        ThreadUtil.delay(120000);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        break;
      }
    }
    if (!success) {
      if (mediaType.equals("show")) {
        log.error(
            "{}Failed to process webhook for for {} S{}E{} - {}{}",
            ConsoleColor.RED.value,
            webhook.metadata.grandparentTitle,
            webhook.metadata.parentIndex,
            webhook.metadata.index,
            webhook.metadata.title,
            ConsoleColor.NONE.value);
      } else {
        log.error(
            "{}Failed to process webhook for {}{}",
            ConsoleColor.RED.value,
            webhook.metadata.title,
            ConsoleColor.NONE.value);
      }
    }
  }

  public void loginUser(String user, String password) {
    log.info("Logging {} in to TVTime...", user);
    for (int i = 1; i <= 5; i++) {
      try {
        tvTimeService.login(user, password);
        log.info(
            "{}{} has been successfully logged in!{}",
            ConsoleColor.GREEN.value,
            user,
            ConsoleColor.NONE.value);
        return;
      } catch (TVTimeException e) {
        log.error(
            "{}TVTime login failed for user {}, please check credentials and restart the application.{}",
            ConsoleColor.RED.value,
            user,
            ConsoleColor.NONE.value);
        log.error(e.getMessage());
      } catch (Exception e) {
        log.warn(e.getMessage(), e);
        log.warn(
            "{}Connection to TV Time failed, will retry in {}s, attempts remaining {}{}",
            ConsoleColor.YELLOW.value,
            (3000 * i) / 1000,
            5 - i,
            ConsoleColor.NONE.value);
        ThreadUtil.delay(3000 * i);
      }
    }
    throw new TVTimeException(
        ConsoleColor.RED.value
            + "Unable to connect to TVTime after multiple attempts, please check your internet connection. It is possible http://tvtime.com is unavailable."
            + ConsoleColor.NONE.value);
  }

  private boolean hasPlexUser(AccountLink tvTimeAccount, String plexUser) {
    for (String user : tvTimeAccount.getPlexUsers().split(",")) {
      if (user.trim().equalsIgnoreCase(plexUser.trim())) {
        return true;
      }
    }
    return false;
  }
}
