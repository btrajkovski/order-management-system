package com.btrajkovski;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.pattern.StatusReply;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import com.btrajkovski.orders.OrderEntity;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderTest {
//    @ClassRule
//    public static final TestKitJunitResource testKit = new TestKitJunitResource(
//            ConfigFactory.load(OrderRoutesTest.class.getClassLoader(), "application-test.conf"));

    @ClassRule
    public static final TestKitJunitResource testKit =
            new TestKitJunitResource(
                    ConfigFactory.load(ConfigFactory.parseString(
                            "akka.actor.serialization-bindings {\n"
                                    + "  \"com.btrajkovski.serializers.JsonSerializable\" = jackson-json\n"
                                    + "}"))
                            .withFallback(EventSourcedBehaviorTestKit.config()));

    private EventSourcedBehaviorTestKit<OrderEntity.Command, OrderEntity.Event, OrderEntity.State>
            eventSourcedTestKit =
            EventSourcedBehaviorTestKit.create(
                    testKit.system(), OrderEntity.create("order-id"));

    @Test
    public void shouldCreateOrder() {
        EventSourcedBehaviorTestKit.CommandResultWithReply<OrderEntity.Command, OrderEntity.Event, OrderEntity.State, StatusReply<OrderEntity.OrderSummary>>
                result = eventSourcedTestKit.runCommand(replyTo -> new OrderEntity.CreateOrder("some GPU", replyTo));

        assertThat(result.reply().isSuccess()).isTrue();

    }

//    @Test
//    public void shouldNotAllowPaymentBeforeAddingItem() {
//        ActorRef<OrderEntity.Command> ordersEntity = testKit.spawn(OrderEntity.create());
//        TestProbe<OrderEntity.OrderCreated> probe = testKit.createTestProbe();
//        ordersEntity.tell(new OrderEntity.CreateOrder(new Order("some GPU", 1), probe.getRef()));
//        OrderEntity.OrderCreated orderCreated = probe.receiveMessage();
//        assertThat(orderCreated.data.items.get(0)).isEqualTo("some GPU");
//    }
}
