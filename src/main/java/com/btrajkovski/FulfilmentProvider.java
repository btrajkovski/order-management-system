package com.btrajkovski;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Random;

public class FulfilmentProvider extends EventSourcedBehavior<FulfilmentProvider.Command, FulfilmentProvider.Event, FulfilmentProvider.State> {
    // this makes the context available to the command handler etc.
    private final ActorContext<Command> context;

    interface Command extends JsonSerializable {
    }

    interface Event extends JsonSerializable {
    }

    public static class State implements JsonSerializable {

        private State() {
        }
    }

    public static class ShipOrder implements Command {

        public ShipOrder() {
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
        return Behaviors.setup(ctx -> new FulfilmentProvider(PersistenceId.ofUniqueId("fulfilment"), ctx));
    }

    private FulfilmentProvider(PersistenceId persistenceId, ActorContext<Command> ctx) {
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

        context.getLog().info("Shipping order with status {}", shipSuccessfully);

        if (shipSuccessfully) {
            return Effect()
                    .persist(new OrderShipped(true));
        } else {
            return Effect()
                    .persist(new ShipmentFailed(false));
        }
    }
}

