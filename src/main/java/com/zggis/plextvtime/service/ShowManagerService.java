package com.zggis.plextvtime.service;

import com.zggis.plextvtime.dto.plex.PlexWebhook;

import java.util.Objects;

public interface ShowManagerService {
  void markAsWatched(PlexWebhook webhook);

  record Show(String name) {
    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      Show show = (Show) o;
      return name.equalsIgnoreCase(show.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name.toLowerCase());
    }
  }
}
