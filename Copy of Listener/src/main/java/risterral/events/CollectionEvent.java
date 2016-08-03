package risterral.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import risterral.events.dto.ItemDTO;

import java.util.List;

public class CollectionEvent extends AbstractHexEvent {

    @JsonProperty("Complete")
    public List<ItemDTO> complete;
}
