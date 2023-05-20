package com.zggis.plextvtime.dto.plex;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class Player {
    public boolean local;
    public String publicAddress;
    public String title;
    public String uuid;
}
