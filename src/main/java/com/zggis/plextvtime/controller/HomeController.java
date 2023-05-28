package com.zggis.plextvtime.controller;

import com.zggis.plextvtime.config.AccountConfig;
import com.zggis.plextvtime.dto.ui.AccountCardDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

@Controller
public class HomeController {

    @Autowired
    private AccountConfig accountConfig;

    @Autowired
    private BuildProperties buildProperties;

    @GetMapping("/")
    public String loadPage(Model model) {
        model.addAttribute("version", buildProperties.getVersion());
        List<AccountCardDTO> accounts = new ArrayList<>();
        accountConfig.getAccounts().forEach(link -> accounts.add(new AccountCardDTO(link)));
        model.addAttribute("accounts", accounts);
        return "home";
    }
}
