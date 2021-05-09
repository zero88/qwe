package io.zero88.qwe.micro.filter;

import io.zero88.qwe.dto.JsonData;
import io.zero88.qwe.micro.http.EventMethodDefinition;
import io.zero88.qwe.micro.servicetype.EventMessageService;
import io.github.zero88.utils.Urls;
import io.vertx.servicediscovery.Record;

import lombok.NonNull;

final class EventServicePathPredicate implements ByPathPredicate {

    @Override
    public boolean test(@NonNull Record record, @NonNull String path) {
        EventMethodDefinition definition = JsonData.convert(
            record.getMetadata().getJsonObject(EventMessageService.EVENT_METHOD_CONFIG), EventMethodDefinition.class);
        return definition.getServicePath().equals(Urls.combinePath(path));
    }

}