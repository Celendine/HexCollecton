package risterral.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "Message")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CollectionEvent.class, name = "Collection"),
        @JsonSubTypes.Type(value = InventoryEvent.class, name = "Inventory")})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AbstractHexEvent {

    @JsonProperty("User")
    public String user;

    public static AbstractHexEvent getEvent(String json) throws EventParsingException {
        try {
            ObjectMapper mapper = new ObjectMapper(new JsonFactory());
            mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
            mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
            return mapper.readValue(json.replaceAll("\\[\\]", "null"), AbstractHexEvent.class);
        } catch (IOException e) {
            throw new EventParsingException("Unexpected event type.", e);
        }
    }
}
