package risterral.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemDTO {

    @JsonProperty("Count")
    public Long count;

    @JsonProperty("Flags")
    public String flags;

    @JsonProperty("Guid")
    public GuidDTO guid;

}
