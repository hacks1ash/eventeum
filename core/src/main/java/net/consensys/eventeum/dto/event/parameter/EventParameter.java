package net.consensys.eventeum.dto.event.parameter;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A parameter included within an event.
 *
 * @param <T> The java type that represents the value of the event.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringParameter.class, name = "address"),
        @JsonSubTypes.Type(value = StringParameter.class, name = "bytes16"),
        @JsonSubTypes.Type(value = StringParameter.class, name = "bytes32"),
        @JsonSubTypes.Type(value = StringParameter.class, name = "string"),
        @JsonSubTypes.Type(value = NumberParameter.class, name = "int256"),
        @JsonSubTypes.Type(value = NumberParameter.class, name = "uint8"),
        @JsonSubTypes.Type(value = NumberParameter.class, name = "uint256"),
        @JsonSubTypes.Type(value = ArrayParameter.class, name = "uint256-array")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface EventParameter<T extends Serializable> extends Serializable{
    String getType();

    T getValue();

    @JsonIgnore
    String getValueString();
}
