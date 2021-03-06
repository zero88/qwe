package io.zero88.qwe.http.client;

import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.zero88.qwe.JsonHelper.Junit4;
import io.zero88.qwe.TestHelper;
import io.zero88.qwe.event.EBContract;
import io.zero88.qwe.event.EventAction;
import io.zero88.qwe.event.EventListener;
import io.zero88.qwe.event.EventMessage;
import io.zero88.qwe.event.EventPattern;
import io.zero88.qwe.event.EventStatus;
import io.zero88.qwe.http.HostInfo;
import io.zero88.qwe.http.client.handler.WebSocketClientPlan;
import io.zero88.qwe.event.EventDirection;

import lombok.RequiredArgsConstructor;

@RunWith(VertxUnitRunner.class)
public class WebSocketClientDelegateTest {

    private static final EventDirection LISTENER = EventDirection.builder()
                                                                 .address("ws.listener")
                                                                 .local(true)
                                                                 .pattern(EventPattern.POINT_2_POINT)
                                                                 .build();
    private static final String PUBLISHER_ADDRESS = "ws.publisher";

    @Rule
    public Timeout timeout = Timeout.seconds(TestHelper.TEST_TIMEOUT_SEC * 2);
    private Vertx vertx;
    private HttpClientConfig config;
    private HostInfo hostInfo;

    @BeforeClass
    public static void beforeClass() {
        TestHelper.setup();
    }

    @Before
    public void setup() {
        vertx = Vertx.vertx();
        config = new HttpClientConfig();
        hostInfo = HostInfo.builder().host("echo.websocket.org").port(80).ssl(false).build();
    }

    @After
    public void teardown(TestContext context) {
        vertx.close(HttpClientRegistry.getInstance().clear());
    }

    @Test
    public void test_not_found(TestContext context) {
        Async async = context.async();
        WebSocketClientDelegate client = WebSocketClientDelegate.create(vertx, config, hostInfo);
        client.open(WebSocketClientPlan.create("/xxx", LISTENER, PUBLISHER_ADDRESS)).onSuccess(msg -> {
            String error
                = "WebSocket connection attempt returned HTTP status code 404 | Cause:  - Error Code: DATA_NOT_FOUND";
            final JsonObject expected = new JsonObject().put("status", EventStatus.FAILED)
                                                        .put("action", "OPEN")
                                                        .put("error", new JsonObject().put("code", "HTTP_ERROR")
                                                                                      .put("message", error));
            Junit4.assertJson(context, async, expected, msg.toJson());
        }).eventually(complete(async));
    }

    @Test
    public void test_connect_failed_due_unknown_dns(TestContext context) {
        Async async = context.async();
        HostInfo opt = HostInfo.builder().host("echo.websocket.test").port(443).ssl(true).build();
        WebSocketClientDelegate client = WebSocketClientDelegate.create(vertx, config, opt);
        client.open(WebSocketClientPlan.create("/echo", LISTENER, PUBLISHER_ADDRESS)).onSuccess(msg -> {
            String error = "Failed when open WebSocket connection | Cause: failed to resolve 'echo.websocket.test'. " +
                           "Exceeded max queries per resolve 4 ";
            final JsonObject expected = new JsonObject().put("status", EventStatus.FAILED)
                                                        .put("action", "OPEN")
                                                        .put("error", new JsonObject().put("code", "HTTP_ERROR")
                                                                                      .put("message", error));
            Junit4.assertJson(context, async, expected, msg.toJson());
        }).eventually(complete(async));
    }

    @Test
    public void test_connect_and_send(TestContext context) {
        Async async = context.async(2);
        WebSocketClientDelegate client = WebSocketClientDelegate.create(vertx, config, hostInfo);
        client.transporter()
              .register(LISTENER.getAddress(), new EventAsserter(context, async, new JsonObject().put("k", 1)));
        client.open(WebSocketClientPlan.create("/echo", LISTENER, PUBLISHER_ADDRESS)).onSuccess(msg -> {
            System.out.println(msg.toJson());
            client.transporter()
                  .publish(PUBLISHER_ADDRESS, EventMessage.initial(EventAction.SEND, new JsonObject().put("k", 1)));
        }).eventually(complete(async));
    }

    @Test
    @Ignore
    //TODO fix cache
    public void test_cache(TestContext context) {
        Async async = context.async(3);
        context.assertTrue(HttpClientRegistry.getInstance().getWsRegistries().isEmpty());

        final WebSocketClientDelegate client1 = WebSocketClientDelegate.create(vertx, config, hostInfo);
        context.assertEquals(1, HttpClientRegistry.getInstance().getWsRegistries().size());
        context.assertEquals(1, HttpClientRegistry.getInstance().getWsRegistries().get(hostInfo).current());

        final WebSocketClientDelegate client2 = WebSocketClientDelegate.create(vertx, config, hostInfo);
        context.assertEquals(1, HttpClientRegistry.getInstance().getWsRegistries().size());
        context.assertEquals(2, HttpClientRegistry.getInstance().getWsRegistries().get(hostInfo).current());

        final HostInfo host2 = HostInfo.builder().host("echo.websocket.google").build();
        final WebSocketClientDelegate client3 = WebSocketClientDelegate.create(vertx, config, host2);
        context.assertEquals(2, HttpClientRegistry.getInstance().getWsRegistries().size());
        context.assertEquals(1, HttpClientRegistry.getInstance().getWsRegistries().get(host2).current());

        final WebSocketClientPlan metadata = WebSocketClientPlan.create("/echo", LISTENER,
                                                                        PUBLISHER_ADDRESS);
        client1.open(metadata)
               .onSuccess(msg -> {
                   System.out.println(msg.toJson());
                   TestHelper.testComplete(async);
               })
               .flatMap(msg -> client2.open(metadata))
               .onSuccess(msg -> {
                   System.out.println(msg.toJson());
                   TestHelper.testComplete(async);
               })
               .flatMap(msg -> client3.open(metadata))
               .eventually(complete(async))
               .map(msg -> client1.close())
               .compose(r -> client2.close())
               .onSuccess(a -> {
                   System.out.println(HttpClientRegistry.getInstance().getWsRegistries().size());
                   context.assertTrue(HttpClientRegistry.getInstance().getWsRegistries().isEmpty());
               })
               .onFailure(context::fail);
    }

    @RequiredArgsConstructor
    static class EventAsserter implements EventListener {

        private final TestContext context;
        private final Async async;
        private final JsonObject expected;

        @EBContract(action = "UNKNOWN")
        public int send(JsonObject data) {
            Junit4.assertJson(context, async, expected, data);
            return 1;
        }

    }

    static Function<Void, Future<Object>> complete(Async async) {
        return v -> {
            TestHelper.testComplete(async);
            return Future.succeededFuture();
        };
    }

}
