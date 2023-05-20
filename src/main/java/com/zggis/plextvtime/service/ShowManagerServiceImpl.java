package com.zggis.plextvtime.service;

import com.zggis.plextvtime.dto.plex.Guid;
import com.zggis.plextvtime.dto.plex.PlexWebhook;
import com.zggis.plextvtime.exception.TVTimeException;
import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@NoArgsConstructor
@Slf4j
public class ShowManagerServiceImpl implements ShowManagerService {

    @Autowired
    private TVTimeService tvTimeService;

    private BlockingQueue<PlexWebhook> queue = new LinkedBlockingQueue<>();

    @PostConstruct
    public void init() {
        log.info("Show manager running!");
        Thread t1 = new Thread(new WebhookProcessor());
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
        log.info("Processing webhook for {} S{}E{} - {}", webhook.metadata.grandparentTitle, webhook.metadata.parentIndex, webhook.metadata.index, webhook.metadata.title);
        String episodeId = null;
        for (Guid guid : webhook.metadata.guid) {
            if (guid.id.contains("tvdb")) {
                episodeId = guid.id.replace("tvdb://", "");
            }
        }
        if (StringUtils.hasText(episodeId)) {
            String result = null;
            //Need to attempt this in a loop with diminishing delays. After failure threshold is reached throw the exception
            for (int i = 1; i <= 5; i++) {
                try {
                    result = tvTimeService.watchEpisode(episodeId);
                    break;
                } catch (TVTimeException e) {
                    delay(3000 * i);
                    tvTimeService.login();
                    delay(3000 * i);
                }
            }
            log.debug(result);
            log.info("{} S{}E{} - {}, was successfully marked as watched", webhook.metadata.grandparentTitle, webhook.metadata.parentIndex, webhook.metadata.index, webhook.metadata.title);
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
