package com.btrajkovski;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class Orders extends EventSourcedBehavior<Orders.Command, Orders.Event, Orders.State> {

    // this makes the context available to the command handler etc.
    private final ActorContext<Command> context;

    interface Command extends JsonSerializable {
    }

    interface Event extends JsonSerializable {
    }

    public static class State {
        private final List<String> items;

        private State(List<String> items) {
            this.items = items;
        }

        public State() {
            this.items = new ArrayList<>();
        }

        public State addItem(String data) {
            List<String> newItems = new ArrayList<>(items);
            newItems.add(data);
            return new State(newItems);
        }

        public State removeItem(String data) {
            List<String> newItems = new ArrayList<>(items);
            newItems.remove(data);
            return new State(newItems);
        }
    }

    public static class CreateOrder implements Command {
        public final String data;
        public final ActorRef<OrderCommandReply> actorReplyTo;

        public CreateOrder(String data, ActorRef<OrderCommandReply> actorReplyTo) {
            this.data = data;
            this.actorReplyTo = actorReplyTo;
        }
    }

    public static class PayOrder implements Command {
        public final String data;
        final ActorRef<OrderPaid> replyTo;

        public PayOrder(String data, ActorRef<OrderPaid> replyTo) {
            this.data = data;
            this.replyTo = replyTo;
        }
    }

    public enum CloseOrder implements Command {
        INSTANCE
    }

    public static class OrderCreated implements Event {
        public final String data;

        @JsonCreator
        public OrderCreated(@JsonProperty("data") String data) {
            this.data = data;
        }
    }

    public static class OrderPaid implements Event {
        public final String data;

        @JsonCreator
        public OrderPaid(@JsonProperty("data") String data) {
            this.data = data;
        }
    }

    public enum OrderClosed implements Event {
        INSTANCE
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(ctx -> new Orders(PersistenceId.ofUniqueId("orders"), ctx));
    }

    private Orders(PersistenceId persistenceId, ActorContext<Command> ctx) {
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
                .build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderCreated.class, (state, event) -> state.addItem(event.data))
                .onEvent(OrderClosed.class, (state, event) -> state)
                .onEvent(OrderPaid.class, (state, event) -> state)
                .build();
    }

    private Effect<Event, State> onCreateOrder(CreateOrder command) {
        context.getLog().info("Creating order");
        return Effect()
                .persist(new OrderCreated(command.data));
    }

    private Effect<Event, State> onPayOrder(PayOrder command) {
        context.getLog().info("Paying order");
        ActorRef<FulfilmentProvider.Command> fulfilmentProvider = context.spawn(FulfilmentProvider.create(), "fulfilment-provider-" + command.data);
        return Effect()
                .persist(new OrderPaid(command.data))
                .thenRun(() -> command.replyTo.tell(new OrderPaid(command.data)))
                .thenRun(() -> fulfilmentProvider.tell(new FulfilmentProvider.ShipOrder()));
    }

    private Effect<Event, State> onCloseOrder(CloseOrder command) {
        context.getLog().info("Closing order");
        return Effect()
                .persist(OrderClosed.INSTANCE)
                .thenStop();
    }
}
