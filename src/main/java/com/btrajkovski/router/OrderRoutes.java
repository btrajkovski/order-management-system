package com.btrajkovski.router;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.*;
import akka.pattern.StatusReply;
import com.btrajkovski.orders.CreateOrderRequest;
import com.btrajkovski.orders.OrderEntity2;
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

    private CompletionStage<OrderEntity2.OrderSummary> getOrder(String id) {
        EntityRef<OrderEntity2.Command> entityRef = sharding.entityRefFor(OrderEntity2.ENTITY_KEY, id);
        return entityRef.askWithStatus(replyTo -> new OrderEntity2.GetOrder(replyTo), askTimeout);
    }

    private CompletionStage<OrderEntity2.OrderSummary> createOrder(CreateOrderRequest createOrderRequest) {
        String orderId = UUID.randomUUID().toString();
        EntityRef<OrderEntity2.Command> entityRef = sharding.entityRefFor(OrderEntity2.ENTITY_KEY, orderId);
        return entityRef.askWithStatus(replyTo -> new OrderEntity2.CreateOrder(createOrderRequest.item, replyTo), askTimeout);
    }

    private CompletionStage<OrderEntity2.OrderSummary> confirmOrder(String orderUuid) {
        EntityRef<OrderEntity2.Command> entityRef = sharding.entityRefFor(OrderEntity2.ENTITY_KEY, orderUuid);
        return entityRef.askWithStatus(replyTo -> new OrderEntity2.PayOrder(replyTo), askTimeout);
    }

    final ExceptionHandler exceptionHandler = ExceptionHandler.newBuilder()
            .match(StatusReply.ErrorMessage.class, exp ->
                    complete(StatusCodes.BAD_REQUEST, exp.getMessage())
            )
            .match(Exception.class, exp ->
                    complete(StatusCodes.INTERNAL_SERVER_ERROR, exp.getMessage())
            )
            .build();

    final RejectionHandler rejectionHandler = RejectionHandler.newBuilder()
            .handle(Rejection.class, rej ->
                    complete(StatusCodes.INTERNAL_SERVER_ERROR, "Default rejection " + rej.toString())
            )
            .handle(AuthorizationFailedRejection.class, rej ->
                    complete(StatusCodes.FORBIDDEN, "You're out of your depth!")
            )
            .handle(ValidationRejection.class, rej ->
                    complete(StatusCodes.INTERNAL_SERVER_ERROR, "That wasn't valid! " + rej.message())
            )
            .handleAll(MethodRejection.class, rejections -> {
                String supported = rejections.stream()
                        .map(rej -> rej.supported().name())
                        .collect(Collectors.joining(" or "));
                return complete(StatusCodes.METHOD_NOT_ALLOWED, "Can't do that! Supported: " + supported + "!");
            })
//            .handleNotFound(complete(StatusCodes.NOT_FOUND, "Not here!"))
            .build();

    /**
     * This method creates one route (of possibly many more that will be part of your Web App)
     */
    //#all-routes
    public Route userRoutes() {
        return handleRejections(rejectionHandler,
                () -> pathPrefix("orders", () ->
                        concat(
                                //#users-get-delete
                                pathEnd(() ->
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
                                //#users-get-delete
                                //#users-get-post
                                path(PathMatchers.segment(), (String orderUuid) ->
                                        get(() ->
                                                        //#retrieve-user-info
                                                        rejectEmptyResponse(() ->
                                                                onSuccess(getOrder(orderUuid), performed -> {
                                                                            log.info("Get order by uuid {}", orderUuid);
                                                                            return complete(StatusCodes.OK, performed, Jackson.marshaller());
                                                                        }
                                                                )
                                                        )
                                                //#retrieve-user-info
                                        )
                                ),
                                //#users-get-post
                                path(PathMatchers.segment().slash("confirm"), (String orderUuid) ->
                                        get(() ->
                                                        //#retrieve-user-info
                                                        rejectEmptyResponse(() ->
                                                                onSuccess(confirmOrder(orderUuid), performed ->
                                                                        complete(StatusCodes.OK, performed, Jackson.marshaller())
                                                                )
                                                        )
                                                //#retrieve-user-info
                                        )
                                )
                        )
                ));
    }
    //#all-routes

}
