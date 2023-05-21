package com.zggis.plextvtime.service;

import com.zggis.plextvtime.dto.plex.Guid;
import com.zggis.plextvtime.dto.plex.PlexWebhook;
import com.zggis.plextvtime.exception.TVTimeException;
import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@NoArgsConstructor
@Slf4j
public class ShowManagerServiceImpl implements ShowManagerService {

    @Value("${plex.user.list}")
    private String plexUserList;

    @Value("${plex.shows.exclude}")
    private String excludedShowList;

    @Value("${plex.shows.include}")
    private String includeShowList;

    private final Set<String> plexUsers = new HashSet<>();

    private final Set<String> excludedShows = new HashSet<>();

    private final Set<String> includedShows = new HashSet<>();

    @Autowired
    private TVTimeService tvTimeService;

    private final BlockingQueue<PlexWebhook> queue = new LinkedBlockingQueue<>();

    @PostConstruct
    public void init() {
        for (String user : plexUserList.split(",")) {
            plexUsers.add(user.toLowerCase());
        }
        if (StringUtils.hasText(excludedShowList))
            for (String show : excludedShowList.split(",")) {
                excludedShows.add(show.toLowerCase());
            }
        if (StringUtils.hasText(includeShowList))
            for (String show : includeShowList.split(",")) {
                includedShows.add(show.toLowerCase());
            }
        Thread t1 = new Thread(new WebhookProcessor());
        t1.start();
        log.info("Show manager running");
    }

    private class WebhookProcessor implements Runnable {

        public void run() {
            while (true) {
                try {
                    processWebhook(queue.take());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (TVTimeException e) {
                    log.error("Unable to authenticate with TVTime, please check your credentials.");
                    log.error(e.getMessage(), e);
                    System.exit(1);
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
        if (!plexUsers.contains(webhook.account.title.toLowerCase())) {
            log.info("Ignoring webhook for plex user '{}', only the following users will be processed: {}", webhook.account.title, plexUserList);
            return;
        }
        if (!webhook.metadata.librarySectionType.equals("show")) {
            log.info("Ignoring webhook for library type '{}', only type 'show' will be processed", webhook.metadata.librarySectionType);
            return;
        }
        if (!excludedShows.isEmpty()) {
            if (excludedShows.contains(webhook.metadata.grandparentTitle.toLowerCase())) {
                log.info("Ignoring webhook for show '{}', its in the excluded list", webhook.metadata.grandparentTitle);
                return;
            }
        } else if (!includedShows.isEmpty()) {
            if (!includedShows.contains(webhook.metadata.grandparentTitle.toLowerCase())) {
                log.info("Ignoring webhook for show '{}', its not in the included list", webhook.metadata.grandparentTitle);
                return;
            }
        }
        if (!webhook.event.equals("media.scrobble")) {
            log.info("Ignoring webhook for event type '{}', only type media.scrobble will be processed", webhook.event);
            return;
        }


        log.info("Processing webhook for {} S{}E{} - {}", webhook.metadata.grandparentTitle, webhook.metadata.parentIndex, webhook.metadata.index, webhook.metadata.title);
        String episodeId = null;
        for (Guid guid : webhook.metadata.guid) {
            if (guid.id.contains("tvdb")) {
                episodeId = guid.id.replace("tvdb://", "");
            }
        }
        if (StringUtils.hasText(episodeId)) {
            boolean success = false;
            for (int i = 1; i <= 5; i++) {
                try {
                    log.debug(tvTimeService.watchEpisode(episodeId));
                    log.info("{} S{}E{} - {}, was successfully marked as watched", webhook.metadata.grandparentTitle, webhook.metadata.parentIndex, webhook.metadata.index, webhook.metadata.title);
                    success = true;
                    break;
                } catch (TVTimeException e) {
                    log.warn("Connection to TV Time failed, will retry in {}s, attempts remaining {}", (6000 * i) / 1000, 5 - i);
                    delay(3000 * i);
                    tvTimeService.login();
                    delay(3000 * i);
                } catch (WebClientRequestException e) {
                    log.error("Unable to reach https://tvtime.com, please check your internet connection, will retry in 2 minutes.");
                    log.debug(e.getMessage(), e);
                    i--;
                    delay(120000);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    break;
                }
            }
            if (!success) {
                log.error("Failed to process webhook for for {} S{}E{} - {}", webhook.metadata.grandparentTitle, webhook.metadata.parentIndex, webhook.metadata.index, webhook.metadata.title);
            }
        }
    }

    private void delay(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
