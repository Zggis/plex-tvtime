package com.zggis.plextvtime.dto.plex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;

@NoArgsConstructor
@Getter
@Setter
public class Metadata {
    public String librarySectionType;
    public String ratingKey;
    public String key;
    public String parentRatingKey;
    public String grandparentRatingKey;
    public String parentGuid;
    public String grandparentGuid;
    public String type;
    public String title;
    public String grandparentKey;
    public String parentKey;
    public String librarySectionTitle;
    public int librarySectionID;
    public String librarySectionKey;
    public String grandparentTitle;
    public String parentTitle;
    public String contentRating;
    public String summary;
    public int index;
    public int parentIndex;
    public double audienceRating;
    public int viewCount;
    public int skipCount;
    public int lastViewedAt;
    public String thumb;
    public String art;
    public String parentThumb;
    public String grandparentThumb;
    public String grandparentArt;
    public String grandparentTheme;
    public int duration;
    public String originallyAvailableAt;
    public int addedAt;
    public String audienceRatingImage;
    public String chapterSource;
    @JsonProperty("Guid")
    public ArrayList<Guid> guid;
    @JsonProperty("Rating")
    public ArrayList<Rating> rating;
    @JsonProperty("Director")
    public ArrayList<Director> director;
    @JsonProperty("Writer")
    public ArrayList<Writer> writer;
    @JsonProperty("Role")
    public ArrayList<Role> role;
}
