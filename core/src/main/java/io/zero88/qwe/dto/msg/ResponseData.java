package io.zero88.qwe.dto.msg;

import java.util.Objects;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.NoArgsConstructor;

//TODO refactor it
@NoArgsConstructor
public final class ResponseData extends AbstractDTO<ResponseData> {

    @Getter
    private HttpResponseStatus status = HttpResponseStatus.OK;

    public ResponseData(JsonObject headers, JsonObject body) {
        super(headers, body);
    }

    public static ResponseData noContent() {
        return new ResponseData().setStatus(HttpResponseStatus.NO_CONTENT);
    }

    public ResponseData setStatus(HttpResponseStatus status) {
        this.status = status;
        return this;
    }

    @JsonIgnore
    public ResponseData setStatus(int status) {
        this.status = HttpResponseStatus.valueOf(status);
        return this;
    }

    @JsonIgnore
    public boolean isError() {
        return Objects.nonNull(this.status) && this.status.code() >= 400;
    }

}
