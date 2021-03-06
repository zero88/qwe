package io.zero88.qwe.http.client;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import io.zero88.qwe.IConfig;
import io.github.zero88.utils.Strings;

public class HttpClientConfigTest {

    @Test
    public void test_serialize_deserialize() throws JSONException {
        HttpClientConfig def = new HttpClientConfig();
        System.out.println(def.toJson().encodePrettily());
        HttpClientConfig config = IConfig.fromClasspath("httpClient.json", HttpClientConfig.class);
        System.out.println(config.toJson().encodePrettily());
        System.out.println(Strings.duplicate("=", 50));
        JSONAssert.assertEquals(def.toJson().encode(), config.toJson().encode(), JSONCompareMode.LENIENT);
    }

}
