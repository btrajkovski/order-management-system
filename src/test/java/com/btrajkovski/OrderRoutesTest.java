package com.btrajkovski;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import akka.pattern.StatusReply;
import akka.persistence.testkit.PersistenceTestKitPlugin;
import akka.persistence.testkit.javadsl.PersistenceTestKit;
import akka.persistence.typed.PersistenceId;
import com.btrajkovski.orders.OrderEntity2;
import com.btrajkovski.router.OrderRoutes;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


public class OrderRoutesTest extends JUnitRouteTest {

    @ClassRule
    public static final TestKitJunitResource testKit =
            new TestKitJunitResource(
                    PersistenceTestKitPlugin.getInstance()
                            .config()
                            .withFallback(ConfigFactory.load(OrderRoutesTest.class.getClassLoader(), "application-test.conf")));

    private TestRoute appRoute;
    private final PersistenceTestKit persistenceTestKit = PersistenceTestKit.create(testKit.system());

    @Before
    public void beforeEach() {
        persistenceTestKit.clearAll();
        persistenceTestKit.resetPolicy();
    }

    @After
    public void afterEach() {
        persistenceTestKit.clearAll();
    }

    @Before
    public void before() {
        OrderRoutes orderRoutes = new OrderRoutes(testKit.system());
        appRoute = testRoute(orderRoutes.userRoutes());

        Cluster cluster = Cluster.get(testKit.system());
        cluster.manager().tell(Join.create(cluster.selfMember().address()));
        ClusterSharding.get(testKit.system())
                .init(
                        Entity.of(
                                OrderEntity2.ENTITY_KEY,
                                entityContext -> OrderEntity2.create(entityContext.getEntityId())));
    }

    @Test
    public void createNewValidOrder() {
        OrderEntity2.OrderSummary orderSummary = appRoute.run(HttpRequest.POST("/orders")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\n" +
                                "    \"userId\": 1,\n" +
                                "    \"item\": \"Asus GTX 2060\"\n" +
                                "}"))
                .assertStatusCode(StatusCodes.CREATED)
                .assertMediaType("application/json")
                .entity(Jackson.unmarshaller(OrderEntity2.OrderSummary.class));

        assertThat(orderSummary).isNotNull();
        assertThat(orderSummary.isShippedSuccessfully).isNull();
        assertThat(orderSummary.id).isNotNull();
        assertThat(orderSummary.state).isEqualTo(OrderEntity2.State.OrderStatus.CREATED);
        assertThat(orderSummary.item).isEqualTo("Asus GTX 2060");

        persistenceTestKit.expectNextPersistedClass(PersistenceId.of(OrderEntity2.ENTITY_KEY.name(), orderSummary.id).id(), OrderEntity2.OrderCreated.class);
    }

    @Test
    public void paymentOfOrder() {
        String itemName = "Logitech MX518";

        TestProbe<StatusReply<OrderEntity2.OrderSummary>> createOrderProbe = testKit.createTestProbe();
        EntityRef<OrderEntity2.Command> entityRef = ClusterSharding.get(testKit.system()).entityRefFor(OrderEntity2.ENTITY_KEY, UUID.randomUUID().toString());
        entityRef.tell(new OrderEntity2.CreateOrder(itemName, createOrderProbe.getRef()));
        OrderEntity2.OrderSummary orderCreated = createOrderProbe.receiveMessage().getValue();

        String orderId = orderCreated.id;
        appRoute.run(HttpRequest.GET(String.format("/orders/%s/confirm", orderId)))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json");

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        TestProbe<StatusReply<OrderEntity2.OrderSummary>> getOrderProbe = testKit.createTestProbe();
        entityRef.tell(new OrderEntity2.GetOrder(getOrderProbe.getRef()));
        StatusReply<OrderEntity2.OrderSummary> orderSummaryStatusReply = getOrderProbe.receiveMessage(Duration.ofMillis(100));

        assertThat(orderSummaryStatusReply.isSuccess()).isTrue();

        OrderEntity2.OrderSummary orderSummary = orderSummaryStatusReply.getValue();
        assertThat(orderSummary).isNotNull();
        assertThat(orderSummary.isShippedSuccessfully).isNotNull();
        assertThat(orderSummary.id).isNotNull();
        assertThat(orderSummary.state).isEqualTo(OrderEntity2.State.OrderStatus.CLOSED);
        assertThat(orderSummary.item).isEqualTo(itemName);

        String persistenceId = PersistenceId.of(OrderEntity2.ENTITY_KEY.name(), orderSummary.id).id();
        persistenceTestKit.expectNextPersistedClass(persistenceId, OrderEntity2.OrderCreated.class);
        persistenceTestKit.expectNextPersistedClass(persistenceId, OrderEntity2.OrderPaid.class);
        persistenceTestKit.expectNextPersistedClass(persistenceId, OrderEntity2.OrderWasInFulfilment.class);
        persistenceTestKit.expectNextPersistedClass(persistenceId, OrderEntity2.OrderClosed.class, Duration.ofMillis(100));
    }


    @Test
    public void getOrderById() {
        String itemName = "Intel i3 9100f";

        TestProbe<StatusReply<OrderEntity2.OrderSummary>> createOrderProbe = testKit.createTestProbe();
        EntityRef<OrderEntity2.Command> entityRef = ClusterSharding.get(testKit.system()).entityRefFor(OrderEntity2.ENTITY_KEY, UUID.randomUUID().toString());
        entityRef.tell(new OrderEntity2.CreateOrder(itemName, createOrderProbe.getRef()));
        OrderEntity2.OrderSummary orderCreated = createOrderProbe.receiveMessage().getValue();

        OrderEntity2.OrderSummary ordersResponse = appRoute.run(HttpRequest.GET("/orders/" + orderCreated.id))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json")
                .entity(Jackson.unmarshaller(OrderEntity2.OrderSummary.class));

        assertThat(ordersResponse.id).isEqualTo(orderCreated.id);
        assertThat(ordersResponse.item).isEqualTo(itemName);
        assertThat(ordersResponse.state).isEqualTo(OrderEntity2.State.OrderStatus.CREATED);
    }
}
