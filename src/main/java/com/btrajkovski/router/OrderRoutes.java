package com.btrajkovski.router;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.*;
import akka.pattern.StatusReply;
import com.btrajkovski.orders.CreateOrderRequest;
import com.btrajkovski.orders.OrderEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static akka.http.javadsl.server.Directives.*;

/**
 * Routes can be defined in separated classes like shown in here
 */
//#user-routes-class
public class OrderRoutes {
    //#user-routes-class
    private static final Logger log = LoggerFactory.getLogger(OrderRoutes.class);
    private final Duration askTimeout;
    private final ClusterSharding sharding;
    private final ActorSystem<?> system;

    public OrderRoutes(ActorSystem<?> system) {
        this.system = system;

        askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
        sharding = ClusterSharding.get(system);
    }

    private CompletionStage<OrderEntity.OrderSummary> getOrder(String id) {
        EntityRef<OrderEntity.Command> entityRef = sharding.entityRefFor(OrderEntity.ENTITY_KEY, id);
        return entityRef.askWithStatus(replyTo -> new OrderEntity.GetOrder(replyTo), askTimeout);
    }

    private CompletionStage<OrderEntity.OrderSummary> createOrder(CreateOrderRequest createOrderRequest) {
        if (createOrderRequest.items.isEmpty()) {
            throw new OrdersValidationException("Order must contain at least 1 item");
        }
        if (createOrderRequest.items.stream().anyMatch(item -> item.length() < 3)) {
            throw new OrdersValidationException("Each item names must contain at least 3 characters");
        }

        String orderId = UUID.randomUUID().toString();
        EntityRef<OrderEntity.Command> entityRef = sharding.entityRefFor(OrderEntity.ENTITY_KEY, orderId);
        return entityRef.askWithStatus(replyTo -> new OrderEntity.CreateOrder(createOrderRequest.items, replyTo), askTimeout);
    }

    private CompletionStage<OrderEntity.OrderSummary> confirmOrder(String orderUuid) {
        EntityRef<OrderEntity.Command> entityRef = sharding.entityRefFor(OrderEntity.ENTITY_KEY, orderUuid);
        return entityRef.askWithStatus(replyTo -> new OrderEntity.PayOrder(replyTo), askTimeout);
    }

    final ExceptionHandler exceptionHandler = ExceptionHandler.newBuilder()
            .match(StatusReply.ErrorMessage.class, exp ->
                    complete(StatusCodes.BAD_REQUEST, exp.getMessage())
            )
            .match(OrdersValidationException.class, exp ->
                    complete(StatusCodes.BAD_REQUEST, exp.getMessage())
            )
            .match(Exception.class, exp ->
                    complete(StatusCodes.INTERNAL_SERVER_ERROR, exp.getMessage())
            )
            .build();

    final RejectionHandler rejectionHandler = RejectionHandler.newBuilder()
            .handle(ValidationRejection.class, rej ->
                    complete(StatusCodes.BAD_REQUEST, "Invalid request: " + rej.message())
            )
            .handle(MalformedRequestContentRejection.class, rej ->
                    complete(StatusCodes.BAD_REQUEST, "Invalid request: " + rej.toString())
            )
            .handleAll(MethodRejection.class, rejections -> {
                String supported = rejections.stream()
                        .map(rej -> rej.supported().name())
                        .collect(Collectors.joining(" or "));
                return complete(StatusCodes.METHOD_NOT_ALLOWED, "Only following methods are supported on this endpoint:" + supported);
            })
            .handle(Rejection.class, rej ->
                    complete(StatusCodes.BAD_REQUEST,  rej.toString())
            )
            .build();

    /**
     * This method creates one route (of possibly many more that will be part of your Web App)
     */
    //#all-routes
    public Route userRoutes() {
        return
                pathPrefix("orders", () ->
                        concat(
                                pathEnd(() ->
                                        //#create-new-order endpoint
                                        post(() ->
                                                entity(
                                                        Jackson.unmarshaller(CreateOrderRequest.class),
                                                        order ->
                                                                onSuccess(createOrder(order), performed -> {
                                                                    log.info("Create result: {}", performed);
                                                                    return complete(StatusCodes.CREATED, performed, Jackson.marshaller());
                                                                })
                                                )
                                        )
                                ),
                                //get-order-by-id endpoint
                                path(PathMatchers.segment(), (String orderUuid) ->
                                        get(() ->
                                                        rejectEmptyResponse(() ->
                                                                onSuccess(getOrder(orderUuid), performed -> {
                                                                            log.info("Get order by uuid {}", orderUuid);
                                                                            return complete(StatusCodes.OK, performed, Jackson.marshaller());
                                                                        }
                                                                )
                                                        )
                                        )
                                ),
                                //#pay-order endpoint
                                path(PathMatchers.segment().slash("confirm"), (String orderUuid) ->
                                        get(() ->
                                                        rejectEmptyResponse(() ->
                                                                onSuccess(confirmOrder(orderUuid), performed ->
                                                                        complete(StatusCodes.OK, performed, Jackson.marshaller())
                                                                )
                                                        )
                                        )
                                )
                        )
                ).seal(rejectionHandler, exceptionHandler);
    }
    //#all-routes

}
