package com.zggis.plextvtime.dto.plex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Player {
    public boolean local;
    public String publicAddress;
    public String title;
    public String uuid;
}
