package com.zggis.plextvtime.dto.ui;

import com.zggis.plextvtime.config.AccountLink;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@NoArgsConstructor
@Getter
@Setter
public class AccountCardDTO {
    private String tvtimeUser;

    private String[] plexUsers;

    private String[] excludedShows;

    private String[] includedShows;

    private boolean showExcluded = true;

    private boolean showIncluded = false;

    public AccountCardDTO(AccountLink link) {
        this.tvtimeUser = link.getTvtimeUser();
        this.plexUsers = link.getPlexUsers().split(",");

        if (!StringUtils.hasText(link.getPlexShowsExclude()) && StringUtils.hasText(link.getPlexShowsInclude())) {
            showExcluded = false;
            showIncluded = true;
        } else if (!StringUtils.hasText(link.getPlexShowsExclude())) {
            showExcluded = false;
            showIncluded = false;
        }
        if (StringUtils.hasText(link.getPlexShowsInclude()))
            this.includedShows = Arrays.stream(link.getPlexShowsInclude().split(","))
                    .map(show -> StringUtils.replace(show.trim(), "%2C", ",")).toArray(String[]::new);
        if (StringUtils.hasText(link.getPlexShowsExclude()))
            this.excludedShows = Arrays.stream(link.getPlexShowsExclude().split(","))
                    .map(show -> StringUtils.replace(show.trim(), "%2C", ",")).toArray(String[]::new);


    }
}
