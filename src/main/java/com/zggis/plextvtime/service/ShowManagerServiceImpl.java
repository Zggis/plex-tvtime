package com.zggis.plextvtime.service;

import com.zggis.plextvtime.dto.plex.Guid;
import com.zggis.plextvtime.dto.plex.PlexWebhook;
import com.zggis.plextvtime.exception.TVTimeException;
import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@NoArgsConstructor
@Slf4j
public class ShowManagerServiceImpl implements ShowManagerService {

    @Autowired
    private TVTimeService tvTimeService;

    @PostConstruct
    public void init() {
        log.info("Show manager running!");
    }

    @Override
    public void markAsWatched(PlexWebhook webhook) {
        String episodeId = null;
        for (Guid guid : webhook.metadata.guid) {
            if (guid.id.contains("tvdb")) {
                episodeId = guid.id.replace("tvdb://", "");
            }
        }
        if (StringUtils.hasText(episodeId)) {
            String result = null;
            try {
                result = tvTimeService.watchEpisode(episodeId);
            } catch (TVTimeException e) {
                delay();
                tvTimeService.login();
                delay();
                result = tvTimeService.watchEpisode(episodeId);
            }
            log.debug(result);
            log.debug("{} S{}E{} - {}, was successfully marked as watched", webhook.metadata.grandparentTitle, webhook.metadata.parentIndex, webhook.metadata.index, webhook.metadata.title);
        }
    }

    private void delay() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
