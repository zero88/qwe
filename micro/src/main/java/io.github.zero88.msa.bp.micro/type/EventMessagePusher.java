package io.github.zero88.msa.bp.micro.type;

import java.util.function.Consumer;

import io.github.zero88.msa.bp.dto.msg.ResponseData;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public interface EventMessagePusher {

    /**
     * Push data via Event Bus then consume reply data
     *
     * @param path          HTTP path
     * @param httpMethod    HTTP Method
     * @param requestData   Request Data
     * @param dataConsumer  Success Data consumer
     * @param errorConsumer Error consumer
     */
    void execute(String path, HttpMethod httpMethod, JsonObject requestData, Consumer<ResponseData> dataConsumer,
                 Consumer<Throwable> errorConsumer);

}
