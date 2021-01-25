package io.github.zero88.qwe.micro;

import java.util.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import io.github.zero88.qwe.IConfig;
import io.github.zero88.qwe.TestHelper;
import io.github.zero88.qwe.event.EventbusClient;
import io.github.zero88.qwe.micro.http.EventMethodDefinition;
import io.reactivex.Single;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.types.HttpLocation;

@RunWith(VertxUnitRunner.class)
public abstract class BaseMicroVerticleTest {

    public static final String EVENT_RECORD_1 = "event.record.1";
    public static final String EVENT_ADDRESS_1 = "event.address.1";
    public static final String EVENT_RECORD_2 = "event.record.2";
    public static final String EVENT_ADDRESS_2 = "event.address.2";
    public static final String HTTP_RECORD = "http.test";
    protected MicroConfig config;
    protected EventbusClient eventbus;
    private Vertx vertx;
    private MicroContext micro;

    @BeforeClass
    public static void init() {
        TestHelper.setup();
    }

    @Before
    public void setup(TestContext context) {
        Async async = context.async(3);
        config = IConfig.fromClasspath("local.json", MicroConfig.class);
        vertx = Vertx.vertx();
        micro = new MicroContext().setup(vertx, config);
        eventbus = EventbusClient.create(vertx, MicroContext.class.getName());
        final ServiceDiscoveryInvoker discovery = micro.getLocalInvoker();
        final Single<Record> record1 = discovery.addHttpRecord(HTTP_RECORD, new HttpLocation().setHost("123.456.0.1")
                                                                                              .setPort(1234)
                                                                                              .setRoot("/api"),
                                                               new JsonObject().put("meta", "test"));
        final Single<Record> record2 = discovery.addEventMessageRecord(EVENT_RECORD_1, EVENT_ADDRESS_1,
                                                                       EventMethodDefinition.createDefault("/path",
                                                                                                           "/:param"));
        final Single<Record> record3 = discovery.addEventMessageRecord(EVENT_RECORD_2, EVENT_ADDRESS_2,
                                                                       EventMethodDefinition.createDefault("/xy",
                                                                                                           "/:z"));
        Single.concat(record1, record2, record3).subscribe(record -> TestHelper.testComplete(async), context::fail);
    }

    @After
    public void tearDown() {
        if (Objects.nonNull(micro)) {
            micro.unregister(Promise.promise());
        }
        vertx.close();
    }

}
