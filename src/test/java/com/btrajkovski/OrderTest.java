package com.btrajkovski;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class OrderTest {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    private static AtomicInteger counter = new AtomicInteger();
    private static String newCartId() {
        return "cart-" + counter.incrementAndGet();
    }

    @Test
    public void shouldAddItem() {
        ActorRef<Orders.Command> orders = testKit.spawn(Orders.create());
        TestProbe<Orders.OrderCreated> probe = testKit.createTestProbe();
        orders.tell(new Orders.CreateOrder("something", null));
        Orders.OrderCreated orderCreated = probe.receiveMessage();
        Assert.assertEquals("something", orderCreated.data);
//        cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
//        StatusReply<ShoppingCart.Summary> result = probe.receiveMessage();
//        assertEquals(42, result.getValue().items.get("foo").intValue());
//        assertFalse(result.getValue().checkedOut);
    }
}
