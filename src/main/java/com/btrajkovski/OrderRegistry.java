package com.btrajkovski;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

//#user-registry-actor
public class OrderRegistry extends AbstractBehavior<OrderRegistry.Command> {

    // actor protocol
    interface Command {
    }

    public static final class GetOrders implements Command {
        public final ActorRef<Orders> replyTo;

        public GetOrders(ActorRef<Orders> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class CreateOrder implements Command {
        public final Order order;
        public final ActorRef<ActionPerformed> replyTo;

        public CreateOrder(Order order, ActorRef<ActionPerformed> replyTo) {
            this.order = order;
            this.replyTo = replyTo;
        }
    }

    public static final class GetOrderResponse {
        public final Optional<Order> maybeUser;

        public GetOrderResponse(Optional<Order> maybeUser) {
            this.maybeUser = maybeUser;
        }
    }

    public static final class GetOrder implements Command {
        public final long id;
        public final ActorRef<GetOrderResponse> replyTo;

        public GetOrder(long id, ActorRef<GetOrderResponse> replyTo) {
            this.id = id;
            this.replyTo = replyTo;
        }
    }


    public static final class DeleteOrder implements Command {
        public final long id;
        public final ActorRef<ActionPerformed> replyTo;

        public DeleteOrder(long id, ActorRef<ActionPerformed> replyTo) {
            this.id = id;
            this.replyTo = replyTo;
        }
    }


    public static final class ActionPerformed implements Command {
        public final String description;

        public ActionPerformed(String description) {
            this.description = description;
        }
    }

    //#order-case-classes
    public static final class Order {
        public final List<String> items;
        public final long id;
        public final long userId;
        public final OrderState orderState;

        public Order(List<String> items,
                     long id,
                     long userId,
                     OrderState orderState) {
            this.items = items;
            this.id = id;
            this.userId = userId;
            this.orderState = orderState;
        }

        @JsonCreator
        public Order(@JsonProperty(value = "item", required = true) String item,
                     @JsonProperty(value = "userId", required = true) long userId) {
            this.items = Collections.singletonList(item);
            this.userId = userId;
            this.id = 0;
            this.orderState = OrderState.CREATED;
        }
    }

    //#item-case-classes
    public static final class Item {
        public final String name;
        public final int quantity;


        @JsonCreator
        public Item(@JsonProperty(value = "name", required = true) String name, @JsonProperty(value = "quantity", required = true) int quantity) {
            this.name = name;
            this.quantity = quantity;
        }
    }

    public enum OrderState {
        CREATED, PAID, IN_FULFILLMENT, CLOSED
    }

    public static final class Orders {
        public final List<Order> ordersList;

        public Orders(List<Order> ordersList) {
            this.ordersList = ordersList;
        }
    }
    //#user-case-classes

    private final List<Order> orders = new ArrayList<>();
    private long orderIdGenerator = 1;

    private OrderRegistry(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(OrderRegistry::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetOrders.class, this::onGetOrders)
                .onMessage(CreateOrder.class, this::onCreateOrder)
                .onMessage(GetOrder.class, this::onGetUser)
                .onMessage(DeleteOrder.class, this::onDeleteUser)
                .build();
    }

    private Behavior<Command> onGetOrders(GetOrders command) {
        // We must be careful not to send out users since it is mutable
        // so for this response we need to make a defensive copy
        command.replyTo.tell(new Orders(Collections.unmodifiableList(new ArrayList<>(orders))));
        return this;
    }

    private Behavior<Command> onCreateOrder(CreateOrder command) {
        var order = new Order(command.order.items, orderIdGenerator++, command.order.userId, OrderState.CREATED);
        orders.add(order);
        command.replyTo.tell(new ActionPerformed(String.format("Order %d created.", order.id)));
        return this;
    }

    private Behavior<Command> onGetUser(GetOrder command) {
        Optional<Order> maybeUser = orders.stream()
                .filter(order -> order.id == command.id)
                .findFirst();
        command.replyTo.tell(new GetOrderResponse(maybeUser));
        return this;
    }

    private Behavior<Command> onDeleteUser(DeleteOrder command) {
        orders.removeIf(order -> order.id == command.id);
        command.replyTo.tell(new ActionPerformed(String.format("Order %d removed.", command.id)));
        return this;
    }

}
//#user-registry-actor