package com.zggis.plextvtime.service;

import com.zggis.plextvtime.exception.TVTimeException;

public interface TVTimeService {
  void login(String user, String password);

  String watchMedia(String user, String mediaId, String mediaType) throws TVTimeException;
}