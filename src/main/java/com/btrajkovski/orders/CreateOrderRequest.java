package com.btrajkovski.orders;

import com.btrajkovski.serializers.JsonSerializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

@ToString
public class CreateOrderRequest implements JsonSerializable {
    public final String item;
    public final long userId;

    @JsonCreator
    public CreateOrderRequest(@JsonProperty(required = true, value = "item") String item, @JsonProperty(value = "userId", required = true) long userId) {
        this.item = item;
        this.userId = userId;
    }
}
