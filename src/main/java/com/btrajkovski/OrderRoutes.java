package com.btrajkovski;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import com.btrajkovski.OrderRegistry.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

/**
 * Routes can be defined in separated classes like shown in here
 */
//#user-routes-class
public class OrderRoutes {
    //#user-routes-class
    private static final Logger log = LoggerFactory.getLogger(OrderRoutes.class);
    private final ActorRef<OrderRegistry.Command> orderRegistryActor;
    private final Duration askTimeout;
    private final Scheduler scheduler;

    public OrderRoutes(ActorSystem<?> system, ActorRef<OrderRegistry.Command> orderRegistryActor) {
        this.orderRegistryActor = orderRegistryActor;
        scheduler = system.scheduler();
        askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    private CompletionStage<OrderRegistry.GetOrderResponse> getOrder(long id) {
        return AskPattern.ask(orderRegistryActor, ref -> new OrderRegistry.GetOrder(id, ref), askTimeout, scheduler);
    }

    private CompletionStage<OrderRegistry.ActionPerformed> deleteOrder(long id) {
        return AskPattern.ask(orderRegistryActor, ref -> new OrderRegistry.DeleteOrder(id, ref), askTimeout, scheduler);
    }

    private CompletionStage<OrderRegistry.Orders> getOrders() {
        return AskPattern.ask(orderRegistryActor, OrderRegistry.GetOrders::new, askTimeout, scheduler);
    }

    private CompletionStage<OrderRegistry.ActionPerformed> createOrder(Order order) {
        return AskPattern.ask(orderRegistryActor, ref -> new OrderRegistry.CreateOrder(order, ref), askTimeout, scheduler);
    }

    /**
     * This method creates one route (of possibly many more that will be part of your Web App)
     */
    //#all-routes
    public Route userRoutes() {
        return pathPrefix("orders", () ->
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
                                                                    log.info("Create result: {}", performed.description);
                                                                    return complete(StatusCodes.CREATED, performed, Jackson.marshaller());
                                                                })
                                                )
                                        )
                                )
                        ),
                        //#users-get-delete
                        //#users-get-post
                        path(PathMatchers.segment(), (String id) ->
                                concat(
                                        get(() ->
                                                        //#retrieve-user-info
                                                        rejectEmptyResponse(() ->
                                                                onSuccess(getOrder(Long.parseLong(id)), performed ->
                                                                        complete(StatusCodes.OK, performed.maybeUser, Jackson.marshaller())
                                                                )
                                                        )
                                                //#retrieve-user-info
                                        ),
                                        delete(() ->
                                                        //#users-delete-logic
                                                        onSuccess(deleteOrder(Long.parseLong(id)), performed -> {
                                                                    log.info("Delete result: {}", performed.description);
                                                                    return complete(StatusCodes.OK, performed, Jackson.marshaller());
                                                                }
                                                        )
                                                //#users-delete-logic
                                        )
                                )
                        )
                        //#users-get-post
                )
        );
    }
    //#all-routes

}
