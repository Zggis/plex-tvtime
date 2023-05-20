package com.zggis.plextvtime;

import com.zggis.plextvtime.service.TVTimeServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlexTvtimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlexTvtimeApplication.class, args);
    }

}
