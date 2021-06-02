package com.btrajkovski;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@ToString
public class Order implements JsonSerializable {
    public final String orderUuid;
    public final List<String> items;
    public final long userId;
    public boolean isShippedSuccessfully;
    public OrderRegistry.OrderState orderState;

//    @JsonCreator
//    public Order(@JsonProperty("items") List<String> items, @JsonProperty("userId") long userId) {
//        this.items = items;
//        this.userId = userId;
//        this.orderUuid = UUID.randomUUID().toString();
//        orderState = OrderRegistry.OrderState.CREATED;
//    }

    @JsonCreator
    public Order(@JsonProperty("item") String item, @JsonProperty("userId") long userId) {
        this.items = Collections.singletonList(item);
        this.userId = userId;
        this.orderUuid = UUID.randomUUID().toString();
        this.orderState = OrderRegistry.OrderState.CREATED;
        this.isShippedSuccessfully = false;
    }

    public void markAsPaid() {
        this.orderState = OrderRegistry.OrderState.PAID;
    }

    public void markAsInFulfillment() {
        this.orderState = OrderRegistry.OrderState.IN_FULFILLMENT;
    }

    public void markAsClosed(boolean isShippedSuccessfully) {
        this.orderState = OrderRegistry.OrderState.CLOSED;
        this.isShippedSuccessfully = isShippedSuccessfully;
    }
}
