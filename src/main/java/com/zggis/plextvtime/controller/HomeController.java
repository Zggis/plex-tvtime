package com.zggis.plextvtime.controller;

import com.zggis.plextvtime.config.AccountConfig;
import com.zggis.plextvtime.service.ShowManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired
    private AccountConfig accountConfig;

    @Autowired
    private BuildProperties buildProperties;

    @Autowired
    private ShowManagerService showManagerService;

    @GetMapping("/")
    public String loadPage(Model model) {
        model.addAttribute("tvTimeUser", accountConfig.getAccounts().get(0).getTvtimeUser());
        model.addAttribute("plexUsers", accountConfig.getAccounts().get(0).getPlexUsers());
        model.addAttribute("version", buildProperties.getVersion());
        if (StringUtils.hasText(accountConfig.getAccounts().get(0).getPlexShowsExclude())) {
            model.addAttribute("plexInclude", "(Overridden by excluded shows)");
        } else if (!StringUtils.hasText(accountConfig.getAccounts().get(0).getPlexShowsInclude())) {
            model.addAttribute("plexInclude", "All");
        } else {
            model.addAttribute("includedShows", showManagerService.getIncludedShows());
        }
        if (!StringUtils.hasText(accountConfig.getAccounts().get(0).getPlexShowsExclude())) {
            model.addAttribute("plexExclude", "None");
        } else {
            model.addAttribute("excludedShows", showManagerService.getExcludedShows());
        }
        return "home";
    }
}
