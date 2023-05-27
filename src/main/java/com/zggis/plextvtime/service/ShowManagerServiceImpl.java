package com.zggis.plextvtime.service;

import com.zggis.plextvtime.config.AccountConfig;
import com.zggis.plextvtime.config.AccountLink;
import com.zggis.plextvtime.dto.plex.Guid;
import com.zggis.plextvtime.dto.plex.PlexWebhook;
import com.zggis.plextvtime.exception.TVTimeException;
import com.zggis.plextvtime.util.ConsoleColor;
import com.zggis.plextvtime.util.ThreadUtil;
import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@NoArgsConstructor
@Slf4j
public class ShowManagerServiceImpl implements ShowManagerService {

    @Autowired
    private AccountConfig accountConfig;

    private final Set<String> plexUsers = new HashSet<>();

    private final Set<Show> excludedShows = new HashSet<>();

    private final Set<Show> includedShows = new HashSet<>();

    @Autowired
    private TVTimeService tvTimeService;

    private final BlockingQueue<PlexWebhook> queue = new LinkedBlockingQueue<>();

    private final Map<String, String> plexUserToTVTimeUserMap = new HashMap<>();

    private final Map<String, AccountLink> tvtimeAccountMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (String user : accountConfig.getAccounts().get(0).getPlexUsers().split(",")) {
            plexUsers.add(user.toLowerCase());
        }
        if (StringUtils.hasText(accountConfig.getAccounts().get(0).getPlexShowsExclude()))
            for (String show : accountConfig.getAccounts().get(0).getPlexShowsExclude().split(",")) {
                excludedShows.add(new Show(StringUtils.replace(show, "%2C", ",").trim()));
            }
        if (StringUtils.hasText(accountConfig.getAccounts().get(0).getPlexShowsInclude()))
            for (String show : accountConfig.getAccounts().get(0).getPlexShowsInclude().split(",")) {
                includedShows.add(new Show(StringUtils.replace(show, "%2C", ",").trim()));
            }

        for (AccountLink account : accountConfig.getAccounts()) {
            loginUser(account.getTvtimeUser(), account.getTvtimePassword());
            tvtimeAccountMap.put(account.getTvtimeUser(), account);
            for (String plexUser : account.getPlexUsers().toLowerCase().split(",")) {
                plexUserToTVTimeUserMap.put(plexUser, account.getTvtimeUser());
            }
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
                    log.warn("{}Unable to process webhook message: {}{}", ConsoleColor.YELLOW.value, e.getMessage(), ConsoleColor.NONE.value);
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

    @Override
    public List<Show> getExcludedShows() {
        return new ArrayList<>(excludedShows);
    }

    @Override
    public List<Show> getIncludedShows() {
        return new ArrayList<>(includedShows);
    }

    private void processWebhook(PlexWebhook webhook) throws TVTimeException {
        if (!plexUsers.contains(webhook.account.title.toLowerCase())) {
            log.info("Ignoring webhook for plex user '{}', only the configured users will be processed", webhook.account.title);
            return;
        }
        if (!webhook.metadata.librarySectionType.equals("show")) {
            log.info("Ignoring webhook for library type '{}', only type 'show' will be processed", webhook.metadata.librarySectionType);
            return;
        }
        if (!excludedShows.isEmpty()) {
            if (excludedShows.contains(new Show(webhook.metadata.grandparentTitle))) {
                log.info("Ignoring webhook for show '{}', its in the excluded list", webhook.metadata.grandparentTitle);
                return;
            }
        } else if (!includedShows.isEmpty()) {
            if (!includedShows.contains(new Show(webhook.metadata.grandparentTitle))) {
                log.info("Ignoring webhook for show '{}', its not in the included list", webhook.metadata.grandparentTitle);
                return;
            }
        }
        if (!webhook.event.equals("media.scrobble")) {
            log.info("Ignoring webhook for event type '{}', only type media.scrobble will be processed", webhook.event);
            return;
        }

        log.info("{}Processing webhook for {} S{}E{} - {}{}", ConsoleColor.CYAN.value, webhook.metadata.grandparentTitle, webhook.metadata.parentIndex, webhook.metadata.index, webhook.metadata.title, ConsoleColor.NONE.value);
        String episodeId = null;
        for (Guid guid : webhook.metadata.guid) {
            if (guid.id.contains("tvdb")) {
                episodeId = guid.id.replace("tvdb://", "");
            }
        }
        if (StringUtils.hasText(episodeId)) {
            boolean success = false;
            String tvtimeUser = plexUserToTVTimeUserMap.get(webhook.account.title.toLowerCase());
            for (int i = 1; i <= 5; i++) {
                try {
                    log.debug(tvTimeService.watchEpisode(tvtimeUser, episodeId));
                    log.info("{}{} S{}E{} - {}, was successfully marked as watched!{}", ConsoleColor.GREEN.value, webhook.metadata.grandparentTitle, webhook.metadata.parentIndex, webhook.metadata.index, webhook.metadata.title, ConsoleColor.NONE.value);
                    success = true;
                    break;
                } catch (TVTimeException e) {
                    log.warn("{}Connection to TV Time failed for user {}, will retry in {}s, attempts remaining {}{}", ConsoleColor.YELLOW.value, tvtimeUser, (6000 * i) / 1000, 5 - i, ConsoleColor.NONE.value);
                    ThreadUtil.delay(3000 * i);
                    AccountLink tvtimeAccount = tvtimeAccountMap.get(tvtimeUser);
                    tvTimeService.login(tvtimeAccount.getTvtimeUser(), tvtimeAccount.getTvtimePassword());
                    ThreadUtil.delay(3000 * i);
                } catch (WebClientRequestException e) {
                    log.error("{}Unable to reach https://tvtime.com, please check your internet connection, will retry in 2 minutes.{}", ConsoleColor.YELLOW.value, ConsoleColor.NONE.value);
                    log.debug(e.getMessage(), e);
                    i--;
                    ThreadUtil.delay(120000);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    break;
                }
            }
            if (!success) {
                log.error("{}Failed to process webhook for for {} S{}E{} - {}{}", ConsoleColor.RED.value, webhook.metadata.grandparentTitle, webhook.metadata.parentIndex, webhook.metadata.index, webhook.metadata.title, ConsoleColor.NONE.value);
            }
        }
    }

    public void loginUser(String user, String password) {
        log.info("Logging {} in to TVTime...", user);
        for (int i = 1; i <= 5; i++) {
            try {
                tvTimeService.login(user, password);
                tvTimeService.fetchProfile(user);
                log.info("{}{} has been successfully logged in!{}", ConsoleColor.GREEN.value, user, ConsoleColor.NONE.value);
                return;
            } catch (TVTimeException e) {
                log.error(e.getMessage());
                System.exit(1);
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                log.warn("{}Connection to TV Time failed, will retry in {}s, attempts remaining {}{}", ConsoleColor.YELLOW.value, (3000 * i) / 1000, 5 - i, ConsoleColor.NONE.value);
                ThreadUtil.delay(3000 * i);
            }
        }
        throw new TVTimeException(ConsoleColor.RED.value + "Unable to connect to TVTime after multiple attempts, please check your internet connection. It is possible http://tvtime.com is unavailable." + ConsoleColor.NONE.value);
    }
}
