package com.zggis.plextvtime.controller;

import com.zggis.plextvtime.service.ShowManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    @Autowired
    private ShowManagerService showManagerService;

    @RequestMapping(value = "/plex", method = RequestMethod.POST)
    public ResponseEntity<String> handlePlexHook(@RequestBody String body) {
        return ResponseEntity.ok("Not Implemented");
    }
}
