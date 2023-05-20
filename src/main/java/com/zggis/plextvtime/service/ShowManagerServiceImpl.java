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

    private Map<String, TVTimeService.Show> shows = new HashMap<>();

    private Map<String, Set<TVTimeService.Episode>> episodes = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Show manager running!");
        refreshShowList();
    }

    @Override
    public void markAsWatched(PlexWebhook webhook) {
        TVTimeService.Show show = shows.get(webhook.metadata.grandparentTitle);
        if (show != null) {
            log.debug("Found matching TVTime show for {}", webhook.metadata.grandparentTitle);
            Set<TVTimeService.Episode> episodeList = episodes.get(show.tvdbId());
            if (CollectionUtils.isEmpty(episodeList)) {
                log.debug("Fetching episodes for {} ({})", webhook.metadata.grandparentTitle, show.tvdbId());
                Set<TVTimeService.Episode> newEpisodes = tvTimeService.getEpisodes(show.tvdbId());
                log.debug("Fetched {} episodes for {} ({})", newEpisodes.size(), webhook.metadata.grandparentTitle, show.tvdbId());
                episodes.put(show.tvdbId(), newEpisodes);
                episodeList = newEpisodes;
            }
            for (TVTimeService.Episode episode : episodeList) {
                if (isEpisodeMatch(webhook, episode)) {
                    log.debug("Episode match found for {}", webhook.metadata.title);
                    try {
                        tvTimeService.watchEpisode(episode.episodeId());
                    } catch (TVTimeException e) {
                        tvTimeService.login();
                        tvTimeService.watchEpisode(episode.episodeId());
                    }
                    log.debug("Episode {} successfully marked as watched", webhook.metadata.title);
                    return;
                }
            }
            log.debug("No match found for episode {}", webhook.metadata.title);
        } else {
            log.debug("{} is not tracked in TVTime", webhook.metadata.grandparentTitle);
        }
    }

    private boolean isEpisodeMatch(PlexWebhook webhook, TVTimeService.Episode episode) {
        for (Guid guid : webhook.metadata.guid) {
            if (guid.id.contains("tvdb")) {
                return episode.episodeId().equals(guid.id.replace("tvdb://", ""));
            }
        }
        return false;
    }

    private void refreshShowList() {
        for (TVTimeService.Show show : tvTimeService.getShows())
            shows.put(show.name(), show);
    }
}
