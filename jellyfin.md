
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