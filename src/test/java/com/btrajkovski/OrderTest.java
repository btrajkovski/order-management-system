package com.btrajkovski;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.testkit.JUnitRouteTest;
import com.btrajkovski.orders.Order;
import com.btrajkovski.orders.OrderEntity;
import com.typesafe.config.ConfigFactory;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderTest  {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(ConfigFactory.load(OrderRoutesTest.class.getClassLoader(), "application-test.conf"));

    @Test
    public void shouldAddItem() {
        ActorRef<OrderEntity.Command> ordersEntity = testKit.spawn(OrderEntity.create(), "something");
        TestProbe<OrderEntity.OrderCreated> probe = testKit.createTestProbe();
        ordersEntity.tell(new OrderEntity.CreateOrder(new Order("some GPU", 1), probe.getRef()));
        OrderEntity.OrderCreated orderCreated = probe.receiveMessage(Duration.ofSeconds(3L));
        Assertions.assertThat(orderCreated.data.items.get(0)).isEqualTo("some GPU");
    }

    @Test
    public void shouldNotAllowPaymentBeforeAddingItem() {
        ActorRef<OrderEntity.Command> ordersEntity = testKit.spawn(OrderEntity.create());
        TestProbe<OrderEntity.OrderCreated> probe = testKit.createTestProbe();
        ordersEntity.tell(new OrderEntity.CreateOrder(new Order("some GPU", 1), probe.getRef()));
        OrderEntity.OrderCreated orderCreated = probe.receiveMessage();
        Assertions.assertThat(orderCreated.data.items.get(0)).isEqualTo("some GPU");
    }
}
