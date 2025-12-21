package com.zggis.plextvtime.service;

import com.zggis.plextvtime.util.ConsoleColor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class DiscordNotificationService {

    @Value("${discord.webhook-url:#{null}}")
    private String webhookUrl;

    @Value("${discord.notify-on-startup:true}")
    private boolean notifyOnStartup;

    @Value("${discord.host-url:#{null}}")
    private String hostUrl;

    @Value("${server.port:8080}")
    private String serverPort;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        if (StringUtils.hasText(webhookUrl)) {
            this.webClient = WebClient.builder()
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            log.info("Discord notifications enabled");
        } else {
            log.debug("Discord webhook URL not configured, notifications disabled");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (notifyOnStartup && StringUtils.hasText(webhookUrl)) {
            sendStartupNotification();
        }
    }

    private void sendStartupNotification() {
        String webhookEndpoint;
        if (StringUtils.hasText(hostUrl)) {
            webhookEndpoint = hostUrl + "/webhook/plex";
        } else {
            webhookEndpoint = "http://[host]:" + serverPort + "/webhook/plex";
        }
        String message = "✅ **Plex-TVTime** is now up and running!\n" +
                "📡 Webhook endpoint ready at port `" + serverPort + "`\n" +
                "🔗 Configure Plex webhook to: `" + webhookEndpoint + "`";
        sendNotification(message);
    }

    public void sendNotification(String message) {
        if (!StringUtils.hasText(webhookUrl)) {
            log.debug("Discord webhook URL not configured, skipping notification");
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("content", message);

            webClient.post()
                    .uri(webhookUrl)
                    .bodyValue(payload.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            response -> log.debug("Discord notification sent successfully"),
                            error -> log.warn("{}Failed to send Discord notification: {}{}",
                                    ConsoleColor.YELLOW.value,
                                    error.getMessage(),
                                    ConsoleColor.NONE.value)
                    );
        } catch (Exception e) {
            log.warn("{}Failed to send Discord notification: {}{}",
                    ConsoleColor.YELLOW.value,
                    e.getMessage(),
                    ConsoleColor.NONE.value);
        }
    }
}
