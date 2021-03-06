package io.zero88.qwe.http.server.gateway;

import io.vertx.ext.web.Router;
import io.zero88.qwe.SharedDataLocalProxy;
import io.zero88.qwe.http.server.HttpServerPluginContext;
import io.zero88.qwe.http.server.config.ApiGatewayConfig;
import io.zero88.qwe.http.server.rest.RestEventApisCreator;

import lombok.NonNull;

public class GatewayRouterCreator extends RestEventApisCreator<ApiGatewayConfig> {

    @Override
    public String function() {
        return "GatewayAPI";
    }

    @Override
    public @NonNull Router subRouter(@NonNull ApiGatewayConfig config, @NonNull SharedDataLocalProxy sharedData) {
        sharedData.addData(HttpServerPluginContext.SERVER_GATEWAY_ADDRESS_DATA_KEY, config.getAddress());
        return super.subRouter(config, sharedData);
    }

}
