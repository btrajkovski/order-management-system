package com.btrajkovski;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Order {
    public final String orderUuid;
    public final List<String> items;
    public final long userId;
    public OrderRegistry.OrderState orderState;

    public Order(List<String> items, long userId) {
        this.items = items;
        this.userId = userId;
        this.orderUuid = UUID.randomUUID().toString();
        orderState = OrderRegistry.OrderState.CREATED;

    }

    public Order(String item, long userId) {
        this.items = Collections.singletonList(item);
        this.userId = userId;
        this.orderUuid = UUID.randomUUID().toString();
        orderState = OrderRegistry.OrderState.CREATED;
    }


}
