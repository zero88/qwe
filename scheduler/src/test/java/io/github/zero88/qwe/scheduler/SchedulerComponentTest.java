package io.github.zero88.qwe.scheduler;

import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.quartz.JobKey;
import org.skyscreamer.jsonassert.Customization;

import io.github.zero88.qwe.EventbusHelper;
import io.github.zero88.qwe.JsonHelper;
import io.github.zero88.qwe.TestHelper;
import io.github.zero88.qwe.component.ComponentTestHelper;
import io.github.zero88.qwe.dto.JsonData;
import io.github.zero88.qwe.event.DeliveryEvent;
import io.github.zero88.qwe.event.EventAction;
import io.github.zero88.qwe.event.EventModel;
import io.github.zero88.qwe.event.EventPattern;
import io.github.zero88.qwe.event.EventbusClient;
import io.github.zero88.qwe.scheduler.mock.MockEventScheduler;
import io.github.zero88.qwe.scheduler.mock.MockEventScheduler.FailureProcessEventSchedulerListener;
import io.github.zero88.qwe.scheduler.mock.JobModelCreator;
import io.github.zero88.qwe.scheduler.mock.MockEventScheduler.MockProcessEventSchedulerListener;
import io.github.zero88.qwe.scheduler.model.job.QWEJobModel;
import io.github.zero88.qwe.scheduler.model.trigger.CronTriggerModel;
import io.github.zero88.qwe.scheduler.model.trigger.PeriodicTriggerModel;
import io.github.zero88.qwe.scheduler.model.trigger.TriggerModel;
import io.github.zero88.qwe.scheduler.service.SchedulerArgs;
import io.github.zero88.utils.DateTimes.Iso8601Parser;
import io.github.zero88.utils.Strings;
import io.github.zero88.utils.UUID64;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class SchedulerComponentTest {

    private static final Function<String, Customization> UTC_DATE = timeKey -> new Customization(
        "data." + timeKey + ".utc", (o1, o2) -> true);
    private static final Function<String, Customization> LOCAL_DATE = timeKey -> new Customization(
        "data." + timeKey + ".local", (o1, o2) -> true);
    private static final Customization SKIP_LOCAL_DATE = LOCAL_DATE.apply("first_fire_time");
    private static final Customization SKIP_UTC_DATE = UTC_DATE.apply("first_fire_time");
    @Rule
    public Timeout timeout = Timeout.seconds(TestHelper.TEST_TIMEOUT_SEC);
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Vertx vertx;
    private SchedulerConfig config;
    private EventbusClient eventbus;

    @BeforeClass
    public static void beforeSuite() {
        TestHelper.setup();
    }

    @Before
    public void before(TestContext context) {
        vertx = Vertx.vertx();
        config = SchedulerConfig.builder().schedulerName(UUID64.random()).build();
        SchedulerVerticle verticle = ComponentTestHelper.deploy(vertx, context, config.toJson(),
                                                                new SchedulerProvider(), folder.getRoot().toPath());
        eventbus = EventbusClient.create(verticle.sharedData());
    }

    @After
    public void after(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test(timeout = 70 * 1000)
    public void test_add_cron_schedule_success(TestContext context) {
        final Async async = context.async(3);
        eventbus.register(MockEventScheduler.PROCESS_EVENT, new MockProcessEventSchedulerListener());
        DeliveryEvent event = initRegisterEvent(JobModelCreator.create("abc"), CronTriggerModel.builder()
                                                                                               .name("t1")
                                                                                               .expr("0 0/1 * 1/1 * ? *")
                                                                                               .build());
        eventbus.fire(event, EventbusHelper.replyAsserter(context, registerAsserter(context, async, "t1", "abc")));
        EventbusHelper.assertReceivedData(vertx, async, MockEventScheduler.CALLBACK_EVENT.getAddress(),
                                          JsonHelper.asserter(context, async, countResp(0)));
    }

    @Test
    public void test_add_periodic_schedule_success(TestContext context) throws InterruptedException {
        final Async async = context.async(4);
        eventbus.register(MockEventScheduler.PROCESS_EVENT, new MockProcessEventSchedulerListener());
        DeliveryEvent event = initRegisterEvent(JobModelCreator.create("xxx"), PeriodicTriggerModel.builder()
                                                                                                   .name("t2")
                                                                                                   .intervalInSeconds(3)
                                                                                                   .repeat(1)
                                                                                                   .build());
        eventbus.fire(event, EventbusHelper.replyAsserter(context, registerAsserter(context, async, "t2", "xxx")));
        final String addr = MockEventScheduler.CALLBACK_EVENT.getAddress();
        CountDownLatch latch = new CountDownLatch(1);
        EventbusHelper.assertReceivedData(vertx, async, addr, o -> {
            latch.countDown();
            JsonHelper.asserter(context, async, countResp(0)).accept(o);
        });
        latch.await(4, TimeUnit.SECONDS);
        EventbusHelper.assertReceivedData(vertx, async, addr, JsonHelper.asserter(context, async, countResp(1)));
    }

    @Test
    public void test_add_one_job_to_different_trigger_should_success(TestContext context) throws InterruptedException {
        final Async async = context.async(2);
        eventbus.register(MockEventScheduler.PROCESS_EVENT, new MockProcessEventSchedulerListener());
        CronTriggerModel cronTrigger = CronTriggerModel.builder().name("t1").expr("0 0/1 * 1/1 * ? *").build();
        PeriodicTriggerModel periodicTrigger = PeriodicTriggerModel.builder().name("t2").intervalInSeconds(3).build();
        DeliveryEvent event1 = initRegisterEvent(JobModelCreator.create("abc"), cronTrigger);
        DeliveryEvent event2 = initRegisterEvent(JobModelCreator.create("abc"), periodicTrigger);
        CountDownLatch latch = new CountDownLatch(1);
        eventbus.fire(event1, e -> {
            latch.countDown();
            EventbusHelper.replyAsserter(context, async, registerResponse("t1", "abc"), SKIP_LOCAL_DATE, SKIP_UTC_DATE)
                          .handle(e);
        });
        latch.await(1, TimeUnit.SECONDS);
        eventbus.fire(event2,
                      EventbusHelper.replyAsserter(context, async, registerResponse("t2", "abc"), SKIP_LOCAL_DATE,
                                                   SKIP_UTC_DATE));
    }

    @Test
    public void test_add_same_trigger_should_failed(TestContext context) throws InterruptedException {
        final Async async = context.async(2);
        eventbus.register(MockEventScheduler.PROCESS_EVENT, new MockProcessEventSchedulerListener());
        PeriodicTriggerModel periodicTrigger = PeriodicTriggerModel.builder()
                                                                   .name("tr3")
                                                                   .repeat(10)
                                                                   .intervalInSeconds(100)
                                                                   .build();
        DeliveryEvent event1 = initRegisterEvent(JobModelCreator.create("j1"), periodicTrigger);
        DeliveryEvent event2 = initRegisterEvent(JobModelCreator.create("j2"), periodicTrigger);
        CountDownLatch latch = new CountDownLatch(1);
        eventbus.fire(event1, e -> {
            latch.countDown();
            EventbusHelper.replyAsserter(context, async, registerResponse("tr3", "j1"), SKIP_LOCAL_DATE, SKIP_UTC_DATE)
                          .handle(e);
        });
        latch.await(1, TimeUnit.SECONDS);
        eventbus.fire(event2, EventbusHelper.replyAsserter(context, async, new JsonObject(
            "{\"status\":\"FAILED\",\"action\":\"CREATE\",\"error\":{\"code\":\"ALREADY_EXIST\"," +
            "\"message\":\"Trigger DEFAULT.tr3 is already assigned to another job DEFAULT.j1\"}}")));
    }

    @Test
    public void test_get_trigger_should_success(TestContext context) throws InterruptedException {
        final Async async = context.async(4);
        eventbus.register(MockEventScheduler.PROCESS_EVENT, new MockProcessEventSchedulerListener());
        CronTriggerModel cron1Trigger = CronTriggerModel.builder().name("tr1").expr("0 0/1 * 1/1 * ? *").build();
        CronTriggerModel cron2Trigger = CronTriggerModel.builder().name("tr2").expr("0 0 12 1/1 * ? *").build();
        PeriodicTriggerModel periodicTrigger = PeriodicTriggerModel.builder()
                                                                   .name("tr3")
                                                                   .intervalInSeconds(1)
                                                                   .repeat(10)
                                                                   .build();
        DeliveryEvent event1 = initRegisterEvent(JobModelCreator.create("j1"), periodicTrigger);
        DeliveryEvent event2 = initRegisterEvent(JobModelCreator.create("j1"), cron1Trigger);
        DeliveryEvent event3 = initRegisterEvent(JobModelCreator.create("j2"), cron2Trigger);
        CountDownLatch latch = new CountDownLatch(3);
        eventbus.fire(event1, e -> {
            latch.countDown();
            EventbusHelper.replyAsserter(context, async, registerResponse("tr3", "j1"), SKIP_LOCAL_DATE, SKIP_UTC_DATE)
                          .handle(e);
        });
        eventbus.fire(event2, e -> {
            latch.countDown();
            EventbusHelper.replyAsserter(context, async, registerResponse("tr1", "j1"), SKIP_LOCAL_DATE, SKIP_UTC_DATE)
                          .handle(e);
        });
        eventbus.fire(event3, e -> {
            latch.countDown();
            EventbusHelper.replyAsserter(context, async, registerResponse("tr2", "j2"), SKIP_LOCAL_DATE, SKIP_UTC_DATE)
                          .handle(e);
        });
        latch.await(1, TimeUnit.SECONDS);
        final JsonObject expected = new JsonObject(
            "{\"status\":\"SUCCESS\",\"action\":\"GET_ONE\",\"data\":{\"prev_fire_time\":null," +
            "\"trigger\":{\"group\":\"DEFAULT\",\"name\":\"tr2\"},\"job\":{\"group\":\"DEFAULT\",\"name\":\"j2\"}," +
            "\"next_fire_time\":{\"local\":\"\",\"utc\":\"\"}}}");
        eventbus.fire(initRegisterEvent(JobModelCreator.create("j2"), cron2Trigger, EventAction.GET_ONE),
                      EventbusHelper.replyAsserter(context, async, expected, LOCAL_DATE.apply("next_fire_time"),
                                                   UTC_DATE.apply("next_fire_time")));
    }

    @Test
    public void test_handler_job_failure_should_success(TestContext context) {
        final Async async = context.async(2);
        final EventModel processEvent = EventModel.clone(MockEventScheduler.PROCESS_EVENT, "event.job.test.failure");
        eventbus.register(processEvent, new FailureProcessEventSchedulerListener());
        PeriodicTriggerModel periodicTrigger = PeriodicTriggerModel.builder().name("tr2").intervalInSeconds(5).build();
        DeliveryEvent event1 = initRegisterEvent(JobModelCreator.create("abc", processEvent), periodicTrigger);
        eventbus.fire(event1,
                      e -> EventbusHelper.replyAsserter(context, async, registerResponse("tr2", "abc"), SKIP_LOCAL_DATE,
                                                        SKIP_UTC_DATE).handle(e));
        final JsonObject failedResp = new JsonObject(
            "{\"status\":\"FAILED\",\"action\":\"MONITOR\"," + "\"error\":{\"code\":\"INVALID_ARGUMENT\"," +
            "\"message\":\"Failed\"}}");
        EventbusHelper.assertReceivedData(vertx, async, config.getMonitorAddress(),
                                          JsonHelper.asserter(context, async, failedResp));
    }

    @Test
    public void test_remove_should_success(TestContext context) throws InterruptedException {
        final Async async = context.async(2);
        eventbus.register(MockEventScheduler.PROCESS_EVENT, new MockProcessEventSchedulerListener());
        final JsonObject payload = new JsonObject().put("job", JsonData.tryParse(new JobKey("abc")).toJson());
        final DeliveryEvent removeEvent = DeliveryEvent.builder()
                                                       .address(config.getRegisterAddress())
                                                       .pattern(EventPattern.REQUEST_RESPONSE)
                                                       .action(EventAction.REMOVE)
                                                       .payload(payload)
                                                       .build();
        JsonObject r = new JsonObject("{\"status\":\"SUCCESS\",\"action\":\"REMOVE\",\"data\":{\"unschedule\":false}}");
        eventbus.fire(removeEvent, EventbusHelper.replyAsserter(context, async, r));
        DeliveryEvent event = initRegisterEvent(JobModelCreator.create("abc"), CronTriggerModel.builder()
                                                                                               .name("tr1")
                                                                                               .expr("0 0/1 * 1/1 * ? *")
                                                                                               .build());
        CountDownLatch latch = new CountDownLatch(1);
        eventbus.fire(event, e -> {
            latch.countDown();
            final JsonObject resp = registerResponse("tr1", "abc");
            EventbusHelper.replyAsserter(context, async, resp, SKIP_LOCAL_DATE, SKIP_UTC_DATE).handle(e);
        });
        latch.await(1, TimeUnit.SECONDS);
        r = new JsonObject("{\"status\":\"SUCCESS\",\"action\":\"REMOVE\",\"data\":{\"unschedule\":true}}");
        eventbus.fire(removeEvent, EventbusHelper.replyAsserter(context, async, r));
    }

    private DeliveryEvent initRegisterEvent(QWEJobModel job, TriggerModel trigger) {
        return initRegisterEvent(job, trigger, EventAction.CREATE);
    }

    private DeliveryEvent initRegisterEvent(QWEJobModel job, TriggerModel trigger, EventAction action) {
        return DeliveryEvent.builder()
                            .address(config.getRegisterAddress())
                            .pattern(EventPattern.REQUEST_RESPONSE)
                            .action(action)
                            .payload(SchedulerArgs.builder().job(job).trigger(trigger).build().toJson())
                            .build();
    }

    private Handler<JsonObject> registerAsserter(TestContext context, Async async, String triggerName, String jobName) {
        return body -> {
            JsonHelper.assertJson(context, async, registerResponse(triggerName, jobName), body, SKIP_LOCAL_DATE,
                                  SKIP_UTC_DATE);
            JsonObject fft = body.getJsonObject("data").getJsonObject("first_fire_time", null);
            context.assertNotNull(fft);
            context.assertTrue(Strings.isNotBlank(fft.getString("local")));
            context.assertTrue(Strings.isNotBlank(fft.getString("utc")));
            context.assertTrue(
                Iso8601Parser.parseZonedDateTime(fft.getString("utc")).getOffset().equals(ZoneOffset.UTC));
        };
    }

    private JsonObject registerResponse(String triggerName, String jobName) {
        return new JsonObject(
            "{\"status\":\"SUCCESS\",\"action\":\"CREATE\",\"data\":{\"trigger\":{\"group\":\"DEFAULT\"," +
            "\"name\":\"" + triggerName + "\"},\"job\":{\"group\":\"DEFAULT\",\"name\":\"" + jobName + "\"}}}");
    }

    private JsonObject countResp(int c) {
        return new JsonObject(
            "{\"status\":\"SUCCESS\",\"action\":\"PUBLISH\",\"prevAction\":\"CREATE\",\"data\":{\"count\":" + c + "}}");
    }

}
