package com.btrajkovski.orders;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import com.btrajkovski.serializers.JsonSerializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderEntity extends EventSourcedBehavior<OrderEntity.Command, OrderEntity.Event, OrderEntity.State> {

    // this makes the context available to the command handler etc.
    private final ActorContext<Command> context;

    public interface Command extends JsonSerializable {
    }

    public interface Event extends JsonSerializable {
    }

    public static class State {
        private final List<Order> orders;

        private State(List<Order> orders) {
            this.orders = orders;
        }

        public State() {
            this.orders = new ArrayList<>();
        }

        public State addOrder(Order order) {
            List<Order> newOrders = new ArrayList<>(orders);
            newOrders.add(order);
            return new State(newOrders);
        }

        public Order getOrder(String orderUuid) {
            return orders.stream()
                    .filter(order -> order.orderUuid.equals(orderUuid))
                    .findFirst()
                    .orElse(null);
        }

        public State markOrderAsPaid(String orderUuid) {
            List<Order> newOrders = new ArrayList<>(orders);
            newOrders.stream()
                    .filter(order -> order.orderUuid.equals(orderUuid))
                    .findFirst()
                    // TODO check previous state
                    .ifPresent(Order::markAsPaid);

            return new State(newOrders);
        }

        public State markOrderAsInFulfilment(String orderUuid) {
            List<Order> newOrders = new ArrayList<>(orders);
            newOrders.stream()
                    .filter(order -> order.orderUuid.equals(orderUuid))
                    .findFirst()
                    .ifPresent(Order::markAsInFulfillment);

            return new State(newOrders);
        }

        public State markOrderAsClosed(String orderUuid, boolean isShippedSuccessfully) {
            List<Order> newOrders = new ArrayList<>(orders);
            newOrders.stream()
                    .filter(order -> order.orderUuid.equals(orderUuid))
                    .findFirst()
                    .ifPresent(order -> order.markAsClosed(isShippedSuccessfully));

            return new State(newOrders);
        }

    }

    public static class CreateOrder implements Command {
        public final Order data;
        public final ActorRef<OrderCreated> actorReplyTo;

        public CreateOrder(Order data, ActorRef<OrderCreated> actorReplyTo) {
            this.data = data;
            this.actorReplyTo = actorReplyTo;
        }
    }

    public static class GetOrders implements Command {
        public final ActorRef<OrdersResponse> actorReplyTo;

        public GetOrders(ActorRef<OrdersResponse> actorReplyTo) {
            this.actorReplyTo = actorReplyTo;
        }
    }

    public static class GetOrder implements Command {
        public final String orderUuid;
        public final ActorRef<Order> actorReplyTo;

        public GetOrder(String orderUuid, ActorRef<Order> actorReplyTo) {
            this.orderUuid = orderUuid;
            this.actorReplyTo = actorReplyTo;
        }
    }

    public static class PayOrder implements Command {
        public final String orderUuid;
        final ActorRef<OrderPaid> replyTo;

        public PayOrder(String orderUuid, ActorRef<OrderPaid> replyTo) {
            this.orderUuid = orderUuid;
            this.replyTo = replyTo;
        }
    }

    public static class CloseOrder implements Command {
        public final String orderUuid;
        public final boolean isShippedSuccessfully;

        public CloseOrder(String orderUuid, boolean isShippedSuccessfully) {
            this.orderUuid = orderUuid;
            this.isShippedSuccessfully = isShippedSuccessfully;
        }
    }

    public static class OrderInFulfilment implements Command {
        public final String orderUuid;

        public OrderInFulfilment(String orderUuid) {
            this.orderUuid = orderUuid;
        }
    }

    @Value
    public static class OrdersResponse {
        public final List<Order> orders;

        @JsonCreator
        public OrdersResponse(@JsonProperty("orders") List<Order> orders) {
            this.orders = orders;
        }
    }

    @Value
    public static class OrderCreated implements Event {
        public final Order data;

        @JsonCreator
        public OrderCreated(@JsonProperty("data") Order data) {
            this.data = data;
        }
    }

    @Value
    public static class OrderPaid implements Event {
        public final String orderUuid;

        @JsonCreator
        public OrderPaid(@JsonProperty("orderUuid") String orderUuid) {
            this.orderUuid = orderUuid;
        }
    }

    public static class OrderWasInFulfilment implements Event {
        public final String orderUuid;

        @JsonCreator
        public OrderWasInFulfilment(@JsonProperty("orderUuid") String orderUuid) {
            this.orderUuid = orderUuid;
        }
    }

    public static class OrderClosed implements Event {
        public final String orderUuid;
        public final boolean isShippedSuccessfully;

        @JsonCreator
        public OrderClosed(@JsonProperty("orderUuid") String orderUuid, @JsonProperty("isShippedSuccessfully") boolean isShippedSuccessfully) {
            this.orderUuid = orderUuid;
            this.isShippedSuccessfully = isShippedSuccessfully;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(ctx -> new OrderEntity(PersistenceId.ofUniqueId("orders"), ctx));
    }

    private OrderEntity(PersistenceId persistenceId, ActorContext<Command> ctx) {
        super(persistenceId);
        this.context = ctx;
    }

    @Override
    public State emptyState() {
        return new State();
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(CreateOrder.class, this::onCreateOrder)
                .onCommand(PayOrder.class, this::onPayOrder)
                .onCommand(CloseOrder.class, this::onCloseOrder)
                .onCommand(GetOrders.class, this::onGetOrders)
                .onCommand(GetOrder.class, this::onGetOrder)
                .onCommand(OrderInFulfilment.class, this::onOrderInFulfilment)
                .build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderCreated.class, (state, event) -> state.addOrder(event.data))
                .onEvent(OrderClosed.class, (state, event) -> state.markOrderAsClosed(event.orderUuid, event.isShippedSuccessfully))
                .onEvent(OrderPaid.class, (state, event) -> state.markOrderAsPaid(event.orderUuid))
                .onEvent(OrderWasInFulfilment.class, (state, event) -> state.markOrderAsInFulfilment(event.orderUuid))
                .build();
    }

    private Effect<Event, State> onCreateOrder(CreateOrder command) {
        context.getLog().info("Creating order");
        return Effect()
                .persist(new OrderCreated(command.data))
                .thenRun(() -> command.actorReplyTo.tell(new OrderCreated(command.data)));
    }

    private Effect<Event, State> onPayOrder(PayOrder command) {
        context.getLog().info("Paying order");
        ActorRef<FulfilmentProvider.Command> fulfilmentProvider = context.spawn(FulfilmentProvider.create(), "fulfilment-provider-" + command.orderUuid);

        return Effect()
                .persist(new OrderPaid(command.orderUuid))
                .thenRun(() -> command.replyTo.tell(new OrderPaid(command.orderUuid)))
                .thenRun(() -> fulfilmentProvider.tell(new FulfilmentProvider.ShipOrder(command.orderUuid, context.getSelf())));
    }

    private Effect<Event, State> onCloseOrder(CloseOrder command) {
        context.getLog().info("Closing order");
        return Effect()
                .persist(new OrderClosed(command.orderUuid, command.isShippedSuccessfully));
    }

    private Effect<Event, State> onGetOrders(State state, GetOrders command) {
        context.getLog().info("Getting all orders");
        return Effect().reply(command.actorReplyTo, new OrdersResponse(Collections.unmodifiableList(state.orders)));
    }

    private Effect<Event, State> onGetOrder(State state, GetOrder command) {
        context.getLog().info("Get order by uuid {}", command.orderUuid);
        return Effect().reply(command.actorReplyTo, state.getOrder(command.orderUuid));
    }

    private Effect<Event, State> onOrderInFulfilment(State state, OrderInFulfilment command) {
        context.getLog().info("Order in fulfilment {}", command.orderUuid);
        return Effect()
                .persist(new OrderWasInFulfilment(command.orderUuid));
    }
}
