package com.zggis.plextvtime.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AccountLink {
    private String tvtimeUser;

    private String tvtimePassword;

    private String plexUsers;

    private String plexShowsExclude;

    private String plexShowsInclude;
}
