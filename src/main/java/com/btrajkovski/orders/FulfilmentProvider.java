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

public class FulfilmentProvider extends EventSourcedBehavior<FulfilmentProvider.Command, FulfilmentProvider.Event, FulfilmentProvider.State> {
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
        private final OrderEntity.OrderSummary orderSummary;
        public final ActorRef<OrderEntity.Command> actorReplyTo;

        public ShipOrder(OrderEntity.OrderSummary orderSummary, ActorRef<OrderEntity.Command> actorReplyTo) {
            this.orderSummary = orderSummary;
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

    public static Behavior<Command> create(String orderId) {
        return Behaviors.setup(ctx -> new FulfilmentProvider(PersistenceId.ofUniqueId("fulfilment" + orderId), ctx));
    }

    private FulfilmentProvider(PersistenceId persistenceId, ActorContext<Command> ctx) {
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
                .onCommand(FulfilmentProvider.ShipOrder.class, this::shipOrder)
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

    private Effect<Event, State> shipOrder(FulfilmentProvider.ShipOrder command) {
        boolean shipSuccessfully = new Random().nextBoolean();

        context.getLog().info("Shipping orders [{}] with status {}", command.orderSummary.items, shipSuccessfully);
        command.actorReplyTo.tell(new OrderEntity.OrderInFulfilment());

        context.getLog().info("Waiting {} seconds before shipping the item", shippingDelay.getSeconds());

        try {
            Thread.sleep(shippingDelay.toMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (shipSuccessfully) {
            return Effect()
                    .persist(new OrderShipped(true))
                    .thenRun(() -> command.actorReplyTo.tell(new OrderEntity.CloseOrder(true)))
                    .thenStop();
        } else {
            return Effect()
                    .persist(new ShipmentFailed(false))
                    .thenRun(() -> command.actorReplyTo.tell(new OrderEntity.CloseOrder(false)))
                    .thenStop();
        }
    }
}

