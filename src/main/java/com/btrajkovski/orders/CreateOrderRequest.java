package com.btrajkovski.orders;

import com.btrajkovski.serializers.JsonSerializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

import java.util.List;

@ToString
public class CreateOrderRequest implements JsonSerializable {
    public final List<String> items;
    public final String userId;

    @JsonCreator
    public CreateOrderRequest(@JsonProperty(required = true, value = "items") List<String> items,
                              @JsonProperty(value = "userId", required = true) String userId) {
        this.items = items;
        this.userId = userId;
    }
}
