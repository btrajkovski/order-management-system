package com.btrajkovski;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AskPattern;
import com.btrajkovski.orders.Order;
import com.btrajkovski.orders.OrderEntity;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletionStage;

public class OrdersMain {
    public static void main(String[] args) {
        System.out.println( "Hello World!" );
        Behavior<OrderEntity.Command> behavior =  OrderEntity.create();
        ActorSystem<OrderEntity.Command> system = ActorSystem.create(behavior, "orders");
        final ActorRef<OrderEntity.Command> ref = system;

        CompletionStage<OrderEntity.OrderCreated> resultOrderCreated = AskPattern.ask(
                ref,
                rep -> new OrderEntity.CreateOrder(new Order("Asus GTX 3060 " + new Random().nextInt(2000) + "$", 1), rep),
                Duration.ofSeconds(5),
                system.scheduler()
        );
        resultOrderCreated.whenComplete(
                (reply, failure) -> {
                    System.out.println("REPLY");
                    System.out.println(reply);
                    CompletionStage<OrderEntity.OrderPaid> resultOrderPaid = AskPattern.ask(
                            ref,
                            rep -> new OrderEntity.PayOrder(reply.data.orderUuid, rep),
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
