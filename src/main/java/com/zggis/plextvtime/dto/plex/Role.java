package com.zggis.plextvtime.dto.plex;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class Role {
    public int id;
    public String filter;
    public String tag;
    public String tagKey;
    public String role;
    public String thumb;
}
