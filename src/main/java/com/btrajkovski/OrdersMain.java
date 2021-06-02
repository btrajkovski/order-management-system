package com.btrajkovski;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AskPattern;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class OrdersMain {
    public static void main(String[] args) {
        System.out.println( "Hello World!" );
        Behavior<Orders.Command> behavior =  Orders.create();
        ActorSystem<Orders.Command> system = ActorSystem.create(behavior, "orders");
        final ActorRef<Orders.Command> ref = system;

        CompletionStage<OrderCommandReply> result1 = AskPattern.ask(
                ref,
                rep -> new Orders.CreateOrder("data", rep),
                Duration.ofSeconds(3),
                system.scheduler()
        );
        result1.whenComplete(
                (reply, failure) -> {
                    System.out.println(reply);
                }
        );

        CompletionStage<Orders.OrderPaid> result2 = AskPattern.ask(
                ref,
                rep -> new Orders.PayOrder("data", rep),
                Duration.ofSeconds(3),
                system.scheduler()
        );
        result2.whenComplete(
                (reply, failure) -> {
                    System.out.println(reply);
                }
        );

    }

}
