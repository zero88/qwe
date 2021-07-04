package io.zero88.qwe.http.server.dynamic.mock;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.zero88.qwe.micro.httpevent.EventMethodDefinition;
import io.zero88.qwe.micro.DiscoveryContext;
import io.zero88.qwe.micro.RecordHelper;
import io.zero88.qwe.micro.ServiceDiscoveryApi;

public class MockEventOneApiMultiLocService extends MockEventOneApiOneLocService {

    private final String address = MockEventServiceListener.TEST_EVENT_4.getAddress();

    @Override
    public void onStart() {
        getEventBus().register(address, MockEventServiceListener.TEST_EVENT_LISTENER_4);
    }

    @Override
    protected void publishService(DiscoveryContext discoveryContext) {
        final ServiceDiscoveryApi discovery = discoveryContext.getDiscovery();
        CompositeFuture.all(
            Stream.of(RecordHelper.create("ems-4", address, EventMethodDefinition.createDefault("/p", "/:pId")),
                      RecordHelper.create("ems-4", address, EventMethodDefinition.createDefault("/c/:cId/p", "/:pId")))
                  .map(discovery::register)
                  .collect(Collectors.toList())).onComplete(AsyncResult::succeeded);
    }

}
