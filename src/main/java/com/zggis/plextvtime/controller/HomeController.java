package com.zggis.plextvtime.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Value("${plex.user.list}")
    private String plexUserList;

    @Value("${tvtime.user}")
    private String tvTimeUser;

    @Value("${plex.shows.exclude}")
    private String plexShowsExclude;

    @Value("${plex.shows.include}")
    private String plexShowsInclude;

    @GetMapping("/")
    public String loadPage(Model model) {
        model.addAttribute("tvTimeUser", tvTimeUser);
        model.addAttribute("plexUsers", plexUserList);
        if (StringUtils.hasText(plexShowsExclude)) {
            model.addAttribute("plexInclude", "(Overridden by excluded shows)");
        } else if (!StringUtils.hasText(plexShowsInclude)) {
            model.addAttribute("plexInclude", "All");
        } else {
            model.addAttribute("plexInclude", plexShowsInclude);
        }
        if (!StringUtils.hasText(plexShowsExclude)) {
            model.addAttribute("plexExclude", "None");
        } else {
            model.addAttribute("plexExclude", plexShowsExclude);
        }
        return "home";
    }
}
