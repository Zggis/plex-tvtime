package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;

import java.util.Objects;
import java.util.Set;

public interface TVTimeService {
    void login();

    String watchEpisode(String episodeId) throws TVTimeException;

    String getUserId();

}
