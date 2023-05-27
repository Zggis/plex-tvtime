package com.zggis.plextvtime.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@ConfigurationProperties(prefix = "account-config")
@Configuration
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AccountConfig {

    private List<AccountLink> accounts;

}
