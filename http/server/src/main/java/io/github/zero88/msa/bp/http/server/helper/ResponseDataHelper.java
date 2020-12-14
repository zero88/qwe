package io.github.zero88.msa.bp.http.server.helper;

import io.github.zero88.msa.bp.dto.msg.ResponseData;
import io.github.zero88.msa.bp.http.server.handler.ResponseDataWriter;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;

@Deprecated
public class ResponseDataHelper {

    public static ResponseData unauthorized() {
        return ResponseDataWriter.serializeResponseData(new JsonObject().put("message", "Unauthorized").encode())
                                 .setStatus(HttpResponseStatus.UNAUTHORIZED);
    }

    public static ResponseData forbidden() {
        return ResponseDataWriter.serializeResponseData(new JsonObject().put("message", "Forbidden").encode())
                                 .setStatus(HttpResponseStatus.FORBIDDEN);
    }

    public static ResponseData badRequest(String message) {
        return ResponseDataWriter.serializeResponseData(new JsonObject().put("message", message).encode())
                                 .setStatus(HttpResponseStatus.BAD_REQUEST);
    }

    public static ResponseData internalServerError(String message) {
        return ResponseDataWriter.serializeResponseData(new JsonObject().put("message", message).encode())
                                 .setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

}
