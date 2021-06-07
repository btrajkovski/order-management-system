package com.btrajkovski.orders;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.pattern.StatusReply;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import com.btrajkovski.serializers.JsonSerializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

import java.util.List;

public class OrderEntity extends EventSourcedBehaviorWithEnforcedReplies<OrderEntity.Command, OrderEntity.Event, OrderEntity.State> {

    // this makes the context available to the command handler etc.
    private final ActorContext<Command> context;

    public interface Command extends JsonSerializable {
    }

    public abstract static class Event implements JsonSerializable {
        public final String orderId;

        @JsonCreator
        public Event(String orderId) {
            this.orderId = orderId;
        }
    }

    public static class State implements JsonSerializable {
        public final List<String> items;
        public final OrderStatus status;
        public final Boolean isShippedSuccessfully;
        public final String userId;

        public State() {
            this(null, null, null, null);
        }

        public State(List<String> items, OrderStatus status, Boolean isShippedSuccessfully, String userId) {
            this.items = items;
            this.status = status;
            this.isShippedSuccessfully = isShippedSuccessfully;
            this.userId = userId;
        }

        public State markOrderAsPaid() {
            return new State(items, OrderStatus.PAID, isShippedSuccessfully, userId);
        }

        public State markOrderAsInFulfilment() {
            return new State(items, OrderStatus.IN_FULFILLMENT, isShippedSuccessfully, userId);
        }

        public State markOrderAsClosed(boolean isShippedSuccessfully) {
            return new State(items, OrderStatus.CLOSED, isShippedSuccessfully, userId);
        }

        public OrderSummary toSummary(String orderId) {
            return new OrderSummary(orderId, items, status, isShippedSuccessfully, userId);
        }
    }

    public enum OrderStatus {
        CREATED, PAID, IN_FULFILLMENT, CLOSED
    }

    public static final EntityTypeKey<Command> ENTITY_KEY =
            EntityTypeKey.create(Command.class, "OrderEntity");

    public static class CreateOrder implements Command {
        public final List<String> items;
        public final String userId;
        public final ActorRef<StatusReply<OrderSummary>> replyTo;

        public CreateOrder(List<String> items, String userId, ActorRef<StatusReply<OrderSummary>> replyTo) {
            this.items = items;
            this.userId = userId;
            this.replyTo = replyTo;
        }
    }

    public static class GetOrder implements Command {
        public final ActorRef<StatusReply<OrderSummary>> replyTo;

        public GetOrder(ActorRef<StatusReply<OrderSummary>> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static class PayOrder implements Command {
        final ActorRef<StatusReply<OrderSummary>> replyTo;

        public PayOrder(ActorRef<StatusReply<OrderSummary>> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static class CloseOrder implements Command {
        public final boolean isShippedSuccessfully;

        public CloseOrder(boolean isShippedSuccessfully) {
            this.isShippedSuccessfully = isShippedSuccessfully;
        }
    }

    public static class OrderInFulfilment implements Command {
        public OrderInFulfilment() {
        }
    }

    @ToString
    public static class OrderSummary implements JsonSerializable {
        public final String id;
        public final List<String> items;
        public final OrderStatus state;
        public final Boolean isShippedSuccessfully;
        public final String userId;

        @JsonCreator
        public OrderSummary(@JsonProperty("id") String id,
                            @JsonProperty("item") List<String> items,
                            @JsonProperty("state") OrderStatus state,
                            @JsonProperty("isShippedSuccessfully") Boolean isShippedSuccessfully,
                            @JsonProperty("userId") String userId) {
            this.id = id;
            this.items = items;
            this.state = state;
            this.isShippedSuccessfully = isShippedSuccessfully;
            this.userId = userId;
        }
    }

    public static class OrderCreated extends Event {
        public final List<String> items;
        public final String userId;

        @JsonCreator
        public OrderCreated(@JsonProperty("orderId") String orderId,
                            @JsonProperty("items") List<String> items,
                            @JsonProperty("userId") String userId) {
            super(orderId);
            this.items = items;
            this.userId = userId;
        }
    }

    public static class OrderPaid extends Event {

        @JsonCreator
        public OrderPaid(String orderId) {
            super(orderId);
        }
    }

    public static class OrderWasInFulfilment extends Event {
        @JsonCreator
        public OrderWasInFulfilment(String orderId) {
            super(orderId);
        }
    }

    public static class OrderClosed extends Event {
        public final boolean isShippedSuccessfully;

        @JsonCreator
        public OrderClosed(String orderId, @JsonProperty("isShippedSuccessfully") boolean isShippedSuccessfully) {
            super(orderId);
            this.isShippedSuccessfully = isShippedSuccessfully;
        }
    }

    private final String orderId;

    public static Behavior<Command> create(String orderId) {
        return Behaviors.setup(ctx -> EventSourcedBehavior.start(new OrderEntity(orderId, ctx), ctx));
    }

    public static void init(ActorSystem<?> system) {
        ClusterSharding.get(system)
                .init(
                        Entity.of(
                                ENTITY_KEY,
                                entityContext -> OrderEntity.create(entityContext.getEntityId())));
    }

    private OrderEntity(String orderId, ActorContext<Command> ctx) {
        super(PersistenceId.of(ENTITY_KEY.name(), orderId));
        this.orderId = orderId;
        this.context = ctx;
    }

    @Override
    public State emptyState() {
        return new State();
    }

    @Override
    public CommandHandlerWithReply<Command, Event, State> commandHandler() {
        CommandHandlerWithReplyBuilder<Command, Event, State> eventsBuilders = newCommandHandlerWithReplyBuilder();

        eventsBuilders.forState(state -> state.status != null)
                .onCommand(GetOrder.class, this::onGetOrder);

        eventsBuilders.forState(state -> state.status == null)
                .onCommand(CreateOrder.class, this::onCreateOrder);

        eventsBuilders.forState(state -> state.status == OrderStatus.CREATED)
                .onCommand(PayOrder.class, this::onPayOrder);

        eventsBuilders.forState(state -> state.status == OrderStatus.PAID)
                .onCommand(OrderInFulfilment.class, this::onOrderInFulfilment);

        eventsBuilders.forState(state -> state.status == OrderStatus.IN_FULFILLMENT)
                .onCommand(CloseOrder.class, this::onCloseOrder);

        // Ignore duplicate OrderInFulfilment commands
        eventsBuilders.forState(state -> state.status == OrderStatus.IN_FULFILLMENT)
                .onCommand(OrderInFulfilment.class, this::ignoreCommand);

        // Negative scenarios
        eventsBuilders.forAnyState()
                .onCommand(CreateOrder.class, this::createNotAllowed)
                .onCommand(PayOrder.class, this::payNotAllowed)
                .onCommand(GetOrder.class, this::orderNotFound);

        return eventsBuilders.build();
    }

    ActorRef<FulfilmentProvider.Command> fulfilmentProvider;

    @Override
    public boolean shouldSnapshot(State state, Event event, long sequenceNr) {
        return event instanceof OrderClosed;
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderCreated.class, (state, event) -> new State(event.items, OrderStatus.CREATED, null, event.userId))
                .onEvent(OrderPaid.class, (state, event) -> {
                    fulfilmentProvider = context.spawn(FulfilmentProvider.create(orderId), "fulfilment-provider-" + orderId);
                    fulfilmentProvider.tell(new FulfilmentProvider.StartShipOrder(state.toSummary(orderId), context.getSelf()));

                    return state.markOrderAsPaid();
                })
                .onEvent(OrderWasInFulfilment.class, (state, event) -> state.markOrderAsInFulfilment())
                .onEvent(OrderClosed.class, (state, event) -> {
                    context.stop(fulfilmentProvider);
                    return state.markOrderAsClosed(event.isShippedSuccessfully);
                })


                .build();
    }

    private ReplyEffect<Event, State> createNotAllowed(CreateOrder command) {
        context.getLog().info("Create order not allowed");
        return Effect().reply(command.replyTo, StatusReply.error("Cannot create an order" + orderId + " that is already created"));
    }

    private ReplyEffect<Event, State> ignoreCommand(Command command) {
        context.getLog().info("Ignoring command {}", command.getClass().getName());
        return Effect().noReply();
    }

    private ReplyEffect<Event, State> orderNotFound(GetOrder command) {
        context.getLog().info("Order not found");
        return Effect().reply(command.replyTo, StatusReply.error("Cannot find an order with id " + orderId));
    }

    private ReplyEffect<Event, State> payNotAllowed(State state, PayOrder command) {
        context.getLog().info("Pay order not allowed");
        return Effect().reply(command.replyTo, StatusReply.error("Cannot pay an order that is in state " + state.status));
    }

    private ReplyEffect<Event, State> onCreateOrder(CreateOrder command) {
        context.getLog().info("Creating order");
        return Effect()
                .persist(new OrderCreated(orderId, command.items, command.userId))
                .thenReply(command.replyTo, newState -> StatusReply.success(newState.toSummary(orderId)));
    }

    private ReplyEffect<Event, State> onPayOrder(PayOrder command) {
        context.getLog().info("Paying order");

        return Effect()
                .persist(new OrderPaid(orderId))
                .thenReply(command.replyTo, newState -> StatusReply.success(newState.toSummary(orderId)));
    }

    private ReplyEffect<Event, State> onCloseOrder(CloseOrder command) {
        context.getLog().info("Closing order");
        return Effect()
                .persist(new OrderClosed(orderId, command.isShippedSuccessfully))
                .thenNoReply();
    }

    private ReplyEffect<Event, State> onGetOrder(State state, GetOrder command) {
        context.getLog().info("Get order by id {}", orderId);
        return Effect().reply(command.replyTo, StatusReply.success(state.toSummary(orderId)));

    }

    private ReplyEffect<Event, State> onOrderInFulfilment(OrderInFulfilment command) {
        context.getLog().info("Order in fulfilment {}", orderId);
        return Effect()
                .persist(new OrderWasInFulfilment(orderId))
                .thenNoReply();
    }
}
