package com.btrajkovski;

//#test-top

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import akka.persistence.testkit.PersistenceTestKitPlugin;
import akka.persistence.testkit.javadsl.PersistenceTestKit;
import com.btrajkovski.orders.Order;
import com.btrajkovski.orders.OrderEntity;
import com.btrajkovski.router.OrderRoutes;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.*;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;


//#set-up
//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OrderRoutesTest extends JUnitRouteTest {

    static Config conf =
            PersistenceTestKitPlugin.getInstance()
                    .config()
                    .withFallback(ConfigFactory.load(OrderRoutesTest.class.getClassLoader(), "application-test.conf"));

    //#test-top
    private static ActorRef<OrderEntity.Command> ordersEntity;
    private TestRoute appRoute;

    @ClassRule
    public static final TestKitJunitResource testKit =
            new TestKitJunitResource(
                    PersistenceTestKitPlugin.getInstance()
                            .config()
                            .withFallback(ConfigFactory.load(OrderRoutesTest.class.getClassLoader(), "application-test.conf")));

    PersistenceTestKit persistenceTestKit = PersistenceTestKit.create(testKit.system());

    @Before
    public void beforeEach() {
        persistenceTestKit.clearAll();
        persistenceTestKit.resetPolicy();
    }

    @After
    public void afterEach() {
        persistenceTestKit.clearAll();
    }

    @BeforeClass
    public static void beforeClass() {
        ordersEntity = testKit.spawn(OrderEntity.create());
    }

    @Before
    public void before() {
        OrderRoutes orderRoutes = new OrderRoutes(testKit.system(), ordersEntity);
        appRoute = testRoute(orderRoutes.userRoutes());
    }

    @AfterClass
    public static void afterClass() {
        testKit.stop(ordersEntity);
    }

    @Test
    public void createNewValidOrder() {
        OrderEntity.OrderCreated orderCreated = appRoute.run(HttpRequest.POST("/orders")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\n" +
                                "    \"userId\": 1,\n" +
                                "    \"item\": \"Asus GTX 2060\"\n" +
                                "}"))
                .assertStatusCode(StatusCodes.CREATED)
                .assertMediaType("application/json")
                .entity(Jackson.unmarshaller(OrderEntity.OrderCreated.class));

        Order data = orderCreated.data;
        assertThat(data).isNotNull();
        assertThat(data.isShippedSuccessfully).isFalse();
        assertThat(data.orderUuid).isNotNull();
        assertThat(data.orderState).isEqualTo(Order.OrderState.CREATED);
        assertThat(data.items).containsExactly("Asus GTX 2060");
        assertThat(data.userId).isEqualTo(1);

        persistenceTestKit.expectNextPersistedClass("orders", OrderEntity.OrderCreated.class);
    }

    @Test
    public void paymentOfOrder() {
        String itemName = "Logitech MX518";
        int userId = 1;

        OrderEntity.OrderCreated orderCreated = createOrder(itemName, userId);

        String orderUuid = orderCreated.data.orderUuid;
        appRoute.run(HttpRequest.GET(String.format("/orders/%s/confirm", orderUuid)))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json");

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        TestProbe<Order> getOrderProbe = testKit.createTestProbe();
        ordersEntity.tell(new OrderEntity.GetOrder(orderUuid, getOrderProbe.getRef()));
        Order order = getOrderProbe.receiveMessage(Duration.ofMillis(100));

        assertThat(order).isNotNull();
        assertThat(order.isShippedSuccessfully).isNotNull();
        assertThat(order.orderUuid).isNotNull();
        assertThat(order.orderState).isEqualTo(Order.OrderState.CLOSED);
        assertThat(order.items).containsExactly(itemName);
        assertThat(order.userId).isEqualTo(userId);

        persistenceTestKit.expectNextPersistedClass("orders", OrderEntity.OrderCreated.class);
        persistenceTestKit.expectNextPersistedClass("orders", OrderEntity.OrderPaid.class);
        persistenceTestKit.expectNextPersistedClass("orders", OrderEntity.OrderWasInFulfilment.class);
        persistenceTestKit.expectNextPersistedClass("orders", OrderEntity.OrderClosed.class, Duration.ofMillis(100));
    }


    @Test
    public void getAllOrders() {
        String itemName = "Intel i3 9100f";
        int userId = 1;
        createOrder(itemName, userId);

        OrderEntity.OrdersResponse ordersResponse = appRoute.run(HttpRequest.GET("/orders"))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json")
                .entity(Jackson.unmarshaller(OrderEntity.OrdersResponse.class));

        assertThat(ordersResponse.orders).isNotEmpty();
        assertThat(ordersResponse.orders.get(0).items).hasSize(1);
        assertThat(ordersResponse.orders)
                .anyMatch(orders -> orders.items.contains(itemName))
                .anyMatch(orders -> orders.userId == userId);
    }

    private OrderEntity.OrderCreated createOrder(String itemName, int userId) {
        TestProbe<OrderEntity.OrderCreated> createOrderProbe = testKit.createTestProbe();
        ordersEntity.tell(new OrderEntity.CreateOrder(new Order(itemName, userId), createOrderProbe.getRef()));
        return createOrderProbe.receiveMessage();
    }
}
