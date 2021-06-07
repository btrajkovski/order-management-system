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
import com.btrajkovski.orders.OrderEntity;
import com.btrajkovski.router.OrderRoutes;
import com.typesafe.config.ConfigFactory;
import org.junit.*;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


public class OrdersIntegrationTest extends JUnitRouteTest {

    @ClassRule
    public static final TestKitJunitResource testKit =
            new TestKitJunitResource(
                    PersistenceTestKitPlugin.getInstance()
                            .config()
                            .withFallback(ConfigFactory.load(OrdersIntegrationTest.class.getClassLoader(), "application-test.conf")));

    private TestRoute appRoute;
    private final PersistenceTestKit persistenceTestKit = PersistenceTestKit.create(testKit.system());

    @BeforeClass
    public static void beforeAll() throws Exception {
        CreateTableTestUtils.createTables(testKit.system());
    }

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
                                OrderEntity.ENTITY_KEY,
                                entityContext -> OrderEntity.create(entityContext.getEntityId())));
    }

    @Test
    public void createNewValidOrder() {
        OrderEntity.OrderSummary orderSummary = appRoute.run(HttpRequest.POST("/orders")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\n" +
                                "    \"userId\": 1,\n" +
                                "    \"items\": [\"Asus GTX 2060\"]\n" +
                                "}"))
                .assertStatusCode(StatusCodes.CREATED)
                .assertMediaType("application/json")
                .entity(Jackson.unmarshaller(OrderEntity.OrderSummary.class));

        assertThat(orderSummary).isNotNull();
        assertThat(orderSummary.isShippedSuccessfully).isNull();
        assertThat(orderSummary.id).isNotNull();
        assertThat(orderSummary.state).isEqualTo(OrderEntity.OrderStatus.CREATED);
        assertThat(orderSummary.items).containsExactly("Asus GTX 2060");

        persistenceTestKit.expectNextPersistedClass(PersistenceId.of(OrderEntity.ENTITY_KEY.name(), orderSummary.id).id(), OrderEntity.OrderCreated.class);
    }

    @Test
    public void throwBadRequestOnInvalidOrder() {
        appRoute.run(HttpRequest.POST("/orders")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\n" +
                                "    \"userId\": 1,\n" +
                                "    \"items\": \"\"\n" +
                                "}"))
                .assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void paymentOfOrder() {
        String itemName = "Logitech MX518";

        TestProbe<StatusReply<OrderEntity.OrderSummary>> createOrderProbe = testKit.createTestProbe();
        EntityRef<OrderEntity.Command> entityRef = ClusterSharding.get(testKit.system()).entityRefFor(OrderEntity.ENTITY_KEY, UUID.randomUUID().toString());
        entityRef.tell(new OrderEntity.CreateOrder(Collections.singletonList(itemName), createOrderProbe.getRef()));
        OrderEntity.OrderSummary orderCreated = createOrderProbe.receiveMessage().getValue();

        String orderId = orderCreated.id;
        appRoute.run(HttpRequest.GET(String.format("/orders/%s/confirm", orderId)))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json");

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        TestProbe<StatusReply<OrderEntity.OrderSummary>> getOrderProbe = testKit.createTestProbe();
        entityRef.tell(new OrderEntity.GetOrder(getOrderProbe.getRef()));
        StatusReply<OrderEntity.OrderSummary> orderSummaryStatusReply = getOrderProbe.receiveMessage(Duration.ofMillis(100));

        assertThat(orderSummaryStatusReply.isSuccess()).isTrue();

        OrderEntity.OrderSummary orderSummary = orderSummaryStatusReply.getValue();
        assertThat(orderSummary).isNotNull();
        assertThat(orderSummary.isShippedSuccessfully).isNotNull();
        assertThat(orderSummary.id).isNotNull();
        assertThat(orderSummary.state).isEqualTo(OrderEntity.OrderStatus.CLOSED);
        assertThat(orderSummary.items).containsExactly(itemName);

        String persistenceId = PersistenceId.of(OrderEntity.ENTITY_KEY.name(), orderSummary.id).id();
        persistenceTestKit.expectNextPersistedClass(persistenceId, OrderEntity.OrderCreated.class);
        persistenceTestKit.expectNextPersistedClass(persistenceId, OrderEntity.OrderPaid.class);
        persistenceTestKit.expectNextPersistedClass(persistenceId, OrderEntity.OrderWasInFulfilment.class);
        persistenceTestKit.expectNextPersistedClass(persistenceId, OrderEntity.OrderClosed.class, Duration.ofMillis(100));
    }

    @Test
    public void getOrderById() {
        String itemName = "Intel i3 9100f";

        TestProbe<StatusReply<OrderEntity.OrderSummary>> createOrderProbe = testKit.createTestProbe();
        EntityRef<OrderEntity.Command> entityRef = ClusterSharding.get(testKit.system()).entityRefFor(OrderEntity.ENTITY_KEY, UUID.randomUUID().toString());
        entityRef.tell(new OrderEntity.CreateOrder(Collections.singletonList(itemName), createOrderProbe.getRef()));
        OrderEntity.OrderSummary orderCreated = createOrderProbe.receiveMessage().getValue();

        OrderEntity.OrderSummary ordersResponse = appRoute.run(HttpRequest.GET("/orders/" + orderCreated.id))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json")
                .entity(Jackson.unmarshaller(OrderEntity.OrderSummary.class));

        assertThat(ordersResponse.id).isEqualTo(orderCreated.id);
        assertThat(ordersResponse.items).containsExactly(itemName);
        assertThat(ordersResponse.state).isEqualTo(OrderEntity.OrderStatus.CREATED);
    }

    @Test
    public void shouldThrowBadRequestIfPayingSameOrderTwice() {
        String itemName = "Logitech MX518";

        TestProbe<StatusReply<OrderEntity.OrderSummary>> createOrderProbe = testKit.createTestProbe();
        EntityRef<OrderEntity.Command> entityRef = ClusterSharding.get(testKit.system()).entityRefFor(OrderEntity.ENTITY_KEY, UUID.randomUUID().toString());
        entityRef.tell(new OrderEntity.CreateOrder(Collections.singletonList(itemName), createOrderProbe.getRef()));
        OrderEntity.OrderSummary orderCreated = createOrderProbe.receiveMessage().getValue();

        String orderId = orderCreated.id;
        appRoute.run(HttpRequest.GET(String.format("/orders/%s/confirm", orderId)))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json");

        appRoute.run(HttpRequest.GET(String.format("/orders/%s/confirm", orderId)))
                .assertStatusCode(StatusCodes.BAD_REQUEST);
    }
}
