package com.btrajkovski.router;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.*;
import com.btrajkovski.orders.Order;
import com.btrajkovski.orders.OrderEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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
    //    private final ActorRef<OrderRegistry.Command> orderRegistryActor;
    private final ActorRef<OrderEntity.Command> ordersActor;
    private final Duration askTimeout;
    private final Scheduler scheduler;

    public OrderRoutes(ActorSystem<?> system, ActorRef<OrderEntity.Command> ordersActor) {
        this.ordersActor = ordersActor;
        scheduler = system.scheduler();
        askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    private CompletionStage<Order> getOrder(String orderUuid) {
        return AskPattern.ask(ordersActor, ref -> new OrderEntity.GetOrder(orderUuid, ref), askTimeout, scheduler);
    }

//    private CompletionStage<OrderRegistry.ActionPerformed> deleteOrder(long id) {
//        return AskPattern.ask(ordersActor, ref -> new OrderRegistry.DeleteOrder(id, ref), askTimeout, scheduler);
//    }

    private CompletionStage<OrderEntity.OrdersResponse> getOrders() {
        return AskPattern.ask(ordersActor, OrderEntity.GetOrders::new, askTimeout, scheduler);
    }

    private CompletionStage<OrderEntity.OrderCreated> createOrder(Order order) {
        return AskPattern.ask(ordersActor, ref -> new OrderEntity.CreateOrder(order, ref), askTimeout, scheduler);
    }

    private CompletionStage<OrderEntity.OrderPaid> confirmOrder(String orderUuid) {
        return AskPattern.ask(ordersActor, ref -> new OrderEntity.PayOrder(orderUuid, ref), askTimeout, scheduler);
    }

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
            .handleNotFound(complete(StatusCodes.NOT_FOUND, "Not here!"))
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
                                                concat(
                                                        get(() ->
                                                                onSuccess(getOrders(),
                                                                        orders -> complete(StatusCodes.OK, orders, Jackson.marshaller())
                                                                )
                                                        ),
                                                        post(() ->
                                                                entity(
                                                                        Jackson.unmarshaller(Order.class),
                                                                        order ->
                                                                                onSuccess(createOrder(order), performed -> {
                                                                                    log.info("Create result: {}", performed.data);
                                                                                    return complete(StatusCodes.CREATED, performed, Jackson.marshaller());
                                                                                })
                                                                )
                                                        )
                                                )
                                        ),
                                        //#users-get-delete
                                        //#users-get-post
                                        path(PathMatchers.segment(), (String orderUuid) ->
                                                        concat(
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
//                                        delete(() ->
//                                                        //#users-delete-logic
//                                                        onSuccess(deleteOrder(id), performed -> {
//                                                                    log.info("Delete result: {}", performed.description);
//                                                                    return complete(StatusCodes.OK, performed, Jackson.marshaller());
//                                                                }
//                                                        )
//                                                //#users-delete-logic
//                                        )
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
