package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;

public interface TVTimeService {
    void login();

    String watchEpisode(String episodeId) throws TVTimeException;

}
