package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;

import java.io.IOException;

public interface TVTimeService {
    void login(String user, String password);

    void fetchProfile(String user) throws IOException;

    String watchEpisode(String user, String episodeId) throws TVTimeException;

}
