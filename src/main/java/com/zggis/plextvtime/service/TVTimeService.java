package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;

import java.util.Objects;
import java.util.Set;

public interface TVTimeService {
    void login();

    Set<TVTimeServiceImpl.Show> getShows() throws TVTimeException;

    Set<TVTimeServiceImpl.Episode> getEpisodes(String tvdbId) throws TVTimeException;

    String watchEpisode(String episodeId) throws TVTimeException;

    String getAvatarImgUrl();

    String getUserId();

    record Show(String name, String tvdbId, String imageUrl) {
        @Override
        public boolean equals(Object o) {
            Show show = (Show) o;
            return tvdbId.equals(show.tvdbId);
        }
    }

    record Episode(String episodeId, String name, String airDate) {
        @Override
        public boolean equals(Object o) {
            Episode episode = (Episode) o;
            return Objects.equals(episodeId, episode.episodeId);
        }
    }
}
