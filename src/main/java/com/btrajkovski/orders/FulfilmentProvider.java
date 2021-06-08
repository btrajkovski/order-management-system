package com.btrajkovski.orders;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import com.btrajkovski.serializers.JsonSerializable;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;
import java.util.Random;

public class FulfilmentProvider extends EventSourcedBehavior<FulfilmentProvider.Command, FulfilmentProvider.Event, FulfilmentProvider.State> {
    // this makes the context available to the command handler etc.
    private final ActorContext<Command> context;
    private final ClusterSharding sharding;
    private final Duration shippingDelay;
    private final TimerScheduler<Command> timers;
    private static final String SHIPPING_TIMER_KEY = "shipping-in-progress";

    interface Command extends JsonSerializable {
    }

    interface Event extends JsonSerializable {
    }

    public static class State implements JsonSerializable {
        private final String orderId;
        private final boolean isOrderProcessed;

        private State(String orderId, boolean isOrderProcessed) {
            this.orderId = orderId;
            this.isOrderProcessed = isOrderProcessed;
        }
    }

    public static class StartShipOrder implements Command {
        private final OrderEntity.OrderSummary orderSummary;
        public final ActorRef<OrderEntity.Command> actorReplyTo;

        public StartShipOrder(OrderEntity.OrderSummary orderSummary, ActorRef<OrderEntity.Command> actorReplyTo) {
            this.orderSummary = orderSummary;
            this.actorReplyTo = actorReplyTo;
        }
    }

    public static class CompleteOrderShipping implements Command {
        public final String replyToEntityId;

        public CompleteOrderShipping(String replyToEntityId) {
            this.replyToEntityId = replyToEntityId;
        }
    }

    public static class ShipmentStarted implements Event {
        public final String orderId;

        @JsonCreator
        public ShipmentStarted(String orderId) {
            this.orderId = orderId;
        }
    }

    public static class OrderShippingEnded implements Event {
        public final boolean isSuccessful;

        @JsonCreator
        public OrderShippingEnded(boolean isSuccessful) {
            this.isSuccessful = isSuccessful;
        }
    }

    public static Behavior<Command> create(String orderId) {
        return Behaviors.setup(ctx ->
                Behaviors.withTimers(timers ->
                        new FulfilmentProvider(PersistenceId.ofUniqueId("fulfilment" + orderId), ctx, timers))
        );
    }

    private FulfilmentProvider(PersistenceId persistenceId, ActorContext<Command> ctx, TimerScheduler<Command> timers) {
        super(persistenceId);
        this.context = ctx;
        this.timers = timers;
        this.sharding = ClusterSharding.get(ctx.getSystem());
        this.shippingDelay = ctx.getSystem().settings().config().getDuration("my-app.fulfilment-provider.shipping-delay");
    }

    @Override
    public State emptyState() {
        return new State(null, false);
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        CommandHandlerBuilder<Command, Event, State> commandHandlerBuilder = newCommandHandlerBuilder();
        commandHandlerBuilder
                .forState(state -> !state.isOrderProcessed)
                .onCommand(StartShipOrder.class, this::shipOrder)
                .onCommand(CompleteOrderShipping.class, this::completeOrderShipment);

        return commandHandlerBuilder.build();
    }


    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(ShipmentStarted.class, (state, event) -> {
                    timers.startSingleTimer(SHIPPING_TIMER_KEY, new CompleteOrderShipping(event.orderId), shippingDelay);
                    return new State(event.orderId, false);
                })
                .onEvent(OrderShippingEnded.class, (state, event) -> {
                    timers.cancel(SHIPPING_TIMER_KEY);
                    return new State(state.orderId, true);
                })
                .build();
    }

    private Effect<Event, State> shipOrder(StartShipOrder command) {
        context.getLog().info("Started shipping of items [{}]", command.orderSummary.items);

        return Effect()
                .persist(new ShipmentStarted(command.orderSummary.id))
                .thenRun(() -> command.actorReplyTo.tell(new OrderEntity.OrderInFulfilment()));
    }

    private Effect<Event, State> completeOrderShipment(CompleteOrderShipping command) {
        boolean shipSuccessfully = new Random().nextBoolean();

        context.getLog().info("Shipment completed with status {} after delay of {} seconds",
                shipSuccessfully, shippingDelay.getSeconds());

        EntityRef<OrderEntity.Command> orderEntity = sharding.entityRefFor(OrderEntity.ENTITY_KEY, command.replyToEntityId);

        return Effect()
                .persist(new OrderShippingEnded(shipSuccessfully))
                .thenRun(() -> orderEntity.tell(new OrderEntity.CloseOrder(shipSuccessfully)))
                .thenStop();
    }
}

