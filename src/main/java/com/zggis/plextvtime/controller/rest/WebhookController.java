package com.zggis.plextvtime.controller.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zggis.plextvtime.dto.plex.PlexWebhook;
import com.zggis.plextvtime.service.MediaManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

  @Autowired private MediaManagerService mediaManagerService;

  @RequestMapping(value = "/plex", method = RequestMethod.POST)
  public ResponseEntity<String> handlePlexHook(
      @RequestParam(required = false) String payload, @RequestBody(required = false) String body) {
    if (!StringUtils.hasText(payload)) {
      payload = body;
    }
    log.trace(payload);
    ObjectMapper mapper = new ObjectMapper();
    PlexWebhook hook;
    try {
      hook = mapper.readValue(payload, PlexWebhook.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    if (hook != null) {
      mediaManagerService.markAsWatched(hook);
      return ResponseEntity.ok("Message Received");
    }
    return ResponseEntity.badRequest().build();
  }
}
