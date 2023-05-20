package com.zggis.plextvtime.service;

import com.zggis.plextvtime.dto.plex.PlexWebhook;

public interface ShowManagerService {
    void markAsWatched(PlexWebhook webhook);
}
