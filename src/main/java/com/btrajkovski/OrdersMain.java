package com.btrajkovski;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AskPattern;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletionStage;

public class OrdersMain {
    public static void main(String[] args) {
        System.out.println( "Hello World!" );
        Behavior<Orders.Command> behavior =  Orders.create();
        ActorSystem<Orders.Command> system = ActorSystem.create(behavior, "orders");
        final ActorRef<Orders.Command> ref = system;

        CompletionStage<Orders.OrderCreated> resultOrderCreated = AskPattern.ask(
                ref,
                rep -> new Orders.CreateOrder(new Order("Asus GTX 3060 " + new Random().nextInt(2000) + "$", 1), rep),
                Duration.ofSeconds(5),
                system.scheduler()
        );
        resultOrderCreated.whenComplete(
                (reply, failure) -> {
                    System.out.println("REPLY");
                    System.out.println(reply);
                    CompletionStage<Orders.OrderPaid> resultOrderPaid = AskPattern.ask(
                            ref,
                            rep -> new Orders.PayOrder(reply.data.orderUuid, rep),
                            Duration.ofSeconds(5),
                            system.scheduler()
                    );
                    resultOrderPaid.whenComplete(
                            (reply2, failure2) -> {
                                System.out.println("Reply paid");
                                System.out.println(reply2);
                            }
                    );
                }
        );

    }

}
