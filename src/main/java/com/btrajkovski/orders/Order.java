package com.btrajkovski.orders;

import com.btrajkovski.serializers.JsonSerializable;
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
    public OrderState orderState;

    public enum OrderState {
        CREATED, PAID, IN_FULFILLMENT, CLOSED
    }

    @JsonCreator
    public Order(@JsonProperty("item") String item, @JsonProperty("userId") long userId) {
        this.items = Collections.singletonList(item);
        this.userId = userId;
        this.orderUuid = UUID.randomUUID().toString();
        this.orderState = OrderState.CREATED;
        this.isShippedSuccessfully = false;
    }

    public void markAsPaid() {
        this.orderState = OrderState.PAID;
    }

    public void markAsInFulfillment() {
        this.orderState = OrderState.IN_FULFILLMENT;
    }

    public void markAsClosed(boolean isShippedSuccessfully) {
        this.orderState = OrderState.CLOSED;
        this.isShippedSuccessfully = isShippedSuccessfully;
    }
}
