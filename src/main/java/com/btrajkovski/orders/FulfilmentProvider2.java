package com.btrajkovski.orders;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import com.btrajkovski.serializers.JsonSerializable;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;
import java.util.Random;

public class FulfilmentProvider2 extends EventSourcedBehavior<FulfilmentProvider2.Command, FulfilmentProvider2.Event, FulfilmentProvider2.State> {
    // this makes the context available to the command handler etc.
    private final ActorContext<Command> context;
    private final Duration shippingDelay;

    interface Command extends JsonSerializable {
    }

    interface Event extends JsonSerializable {
    }

    public static class State implements JsonSerializable {

        private State() {
        }
    }

    public static class ShipOrder implements Command {
        private final String orderId;
        public final ActorRef<OrderEntity2.Command> actorReplyTo;

        public ShipOrder(String orderId, ActorRef<OrderEntity2.Command> actorReplyTo) {
            this.orderId = orderId;
            this.actorReplyTo = actorReplyTo;
        }
    }

    public static class OrderShipped implements Event {
        public final boolean isSuccessful;

        @JsonCreator
        public OrderShipped(boolean isSuccessful) {
            this.isSuccessful = isSuccessful;
        }
    }

    public static class ShipmentFailed implements Event {
        public final boolean isSuccessful;

        @JsonCreator
        public ShipmentFailed(boolean isSuccessful) {
            this.isSuccessful = isSuccessful;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(ctx -> new FulfilmentProvider2(PersistenceId.ofUniqueId("fulfilment"), ctx));
    }

    private FulfilmentProvider2(PersistenceId persistenceId, ActorContext<Command> ctx) {
        super(persistenceId);
        this.context = ctx;
        this.shippingDelay = ctx.getSystem().settings().config().getDuration("my-app.fulfilment-provider.shipping-delay");
    }

    @Override
    public State emptyState() {
        return new State();
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(FulfilmentProvider2.ShipOrder.class, this::shipOrder)
                .build();
    }


    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderShipped.class, (state, event) -> state)
                .onEvent(ShipmentFailed.class, (state, event) -> state)
                .build();
    }

    // maybe use whole order instead of uuid
    private Effect<Event, State> shipOrder(FulfilmentProvider2.ShipOrder command) {
        boolean shipSuccessfully = new Random().nextBoolean();

        context.getLog().info("Shipping order with status {}", shipSuccessfully);
        command.actorReplyTo.tell(new OrderEntity2.OrderInFulfilment());

        context.getLog().info("Waiting {} seconds before shipping the item", shippingDelay.getSeconds());

        try {
            Thread.sleep(shippingDelay.toMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (shipSuccessfully) {
            return Effect()
                    .persist(new OrderShipped(true))
                    .thenRun(() -> command.actorReplyTo.tell(new OrderEntity2.CloseOrder(true)))
                    .thenStop();
        } else {
            return Effect()
                    .persist(new ShipmentFailed(false))
                    .thenRun(() -> command.actorReplyTo.tell(new OrderEntity2.CloseOrder(false)))
                    .thenStop();
        }
    }
}

