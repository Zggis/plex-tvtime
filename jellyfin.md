## Jellyfin Integration

Install Plex-TVTime using the steps found in the project's [README](https://github.com/Zggis/plex-tvtime/blob/42-adding-jellyfin-support/README.md)

Install the Webhook Plugin for Jellyfin: https://github.com/jellyfin/jellyfin-plugin-webhook/tree/master through the Jellyfin server dashboard.

### Configuration
Open the Jellyfin Webhook settings.

Server URL at the top can be left blank, unless you need it for a different Webhook.

Create a new webhook 'Add Generic Destination'

Give the Webhook a name.

Set Webhook URL to http://{HOST}:{PORT}/webhook/plex (You can confirm this URL by opening the Plex-TVTime WebUI)

Make sure 'Status' is set to Enable

Set Notification Type to 'Playback Stop'

Set 'User Filter' if desired.

Set Item Type to 'Episodes' (Movie support should also work if the TVDB metadata is set correctly in the title)

Add the following Template.
```agsl
{
    {{#if_equals PlayedToCompletion 'True'}}
    "event": "media.scrobble",
    {{else}}
    "event": "media.stop",
    {{/if_equals}}
    "Account":
    {
        "title": "{{NotificationUsername}}"
    },
    "Metadata":
    {
        {{#if_equals ItemType 'Episode'}}
        "librarySectionType": "show",
        "type": "episode",
        {{else}}
        "librarySectionType": "movie",
        "type": "movie",
        {{/if_equals}}
        "title": "{{Name}}",
        {{#if_equals ItemType 'Episode'}}
        "grandparentTitle": "{{SeriesName}}",
        "parentTitle": "Season {{SeasonNumber}}",
        "index": {{EpisodeNumber}},
        "parentIndex": {{SeasonNumber}},
        {{/if_equals}}
        "year": {{Year}},
        "Guid":
        [
            {
                "id": "imdb://{{Provider_imdb}}"
            },
            {
                "id": "tvdb://{{Provider_tvdb}}"
            }
        ]
    }
}
```

Save the changes.

### Usage
When watching a TV episode if the playback is stopped near the end of the episode it should mark the episode as watched in TVTime. Unlike Plex, the percentage the episode needs to be played to consider it watched is not configurable, and I am not sure what Jellyfin sets this too. The best way to test this is to play an episode and skip forward all the way to the end and let the duration time run down to 0 and automatically close the player.<br>
If using the advanced Plex-TVTime configuration with multiple users, you can set your Jellyfin users in the 'plexUsers' field.

### Support
Jellyfin integration may have some issues, Plex-TVTime is tested primarily with Plex. When opening an issue be sure to set LOGGING_LEVEL environment variable to TRACE to capture the full webhook payload in the logs and include them in the issue.
