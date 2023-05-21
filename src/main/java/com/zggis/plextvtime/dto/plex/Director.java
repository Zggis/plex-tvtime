package com.zggis.plextvtime.dto.plex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Director {
    public int id;
    public String filter;
    public String tag;
    public String tagKey;
    public String thumb;
}
