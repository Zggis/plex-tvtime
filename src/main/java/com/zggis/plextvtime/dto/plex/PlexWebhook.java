package com.zggis.plextvtime.dto.plex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class PlexWebhook {
    public String event;
    public boolean user;
    public boolean owner;
    @JsonProperty("Account")
    public Account account;
    @JsonProperty("Server") 
    public Server server;
    @JsonProperty("Player") 
    public Player player;
    @JsonProperty("Metadata") 
    public Metadata metadata;
}
