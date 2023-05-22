package com.zggis.plextvtime.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class BeanConfig {

    @Autowired
    private BuildProperties buildProperties;

    @PostConstruct
    public void init() {
        log.info("Running Plex-TVTime v{}", buildProperties.getVersion());
    }
}
