package io.github.zero88.msa.bp.http.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.zero88.msa.bp.http.HostInfo;
import io.reactivex.Observable;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HttpClientRegistry {

    private static HttpClientRegistry instance;
    @Getter(value = AccessLevel.PACKAGE)
    private final Map<HostInfo, ClientStorage<HttpClientDelegate>> httpRegistries = new ConcurrentHashMap<>();
    @Getter(value = AccessLevel.PACKAGE)
    private final Map<HostInfo, ClientStorage<WebSocketClientDelegate>> wsRegistries = new ConcurrentHashMap<>();

    public static HttpClientRegistry getInstance() {
        if (Objects.nonNull(instance)) {
            return instance;
        }
        synchronized (HttpClientRegistry.class) {
            if (Objects.nonNull(instance)) {
                return instance;
            }
            instance = new HttpClientRegistry();
            return instance;
        }
    }

    public WebSocketClientDelegate getWebSocket(@NonNull HostInfo hostInfo,
                                                @NonNull Supplier<WebSocketClientDelegate> fallback) {
        return wsRegistries.computeIfAbsent(hostInfo, hf -> new ClientStorage<>(hf, fallback.get())).tickAndGet();
    }

    public HttpClientDelegate getHttpClient(@NonNull HostInfo hostInfo,
                                            @NonNull Supplier<HttpClientDelegate> fallback) {
        return httpRegistries.computeIfAbsent(hostInfo, hf -> new ClientStorage<>(hf, fallback.get())).tickAndGet();
    }

    public void remove(@NonNull HostInfo hostInfo, boolean isWebsocket) {
        ClientStorage storage = isWebsocket ? wsRegistries.get(hostInfo) : httpRegistries.get(hostInfo);
        if (Objects.isNull(storage)) {
            return;
        }
        if (storage.shouldClose()) {
            storage.get().close().subscribe(() -> {
                if (isWebsocket) {
                    wsRegistries.remove(hostInfo);
                } else {
                    httpRegistries.remove(hostInfo);
                }
            });
        }
    }

    /**
     * Must be call before closing {@code Vertx}
     *
     * @return promise
     */
    public Promise<Void> clear() {
        Promise<Void> promise = Promise.promise();
        Observable.fromIterable(Stream.concat(httpRegistries.values().stream(), wsRegistries.values().stream())
                                      .collect(Collectors.toList()))
                  .map(ClientStorage::get)
                  .map(IClientDelegate::close)
                  .count()
                  .doOnSuccess(c -> log.debug("Closed {} HTTP client(s)", c))
                  .ignoreElement()
                  .subscribe(promise::complete, err -> {
                      log.debug("Something error when closing http client", err);
                      promise.complete();
                  });
        return promise;
    }

    @RequiredArgsConstructor
    static final class ClientStorage<T extends IClientDelegate> implements Supplier<T> {

        private static final Logger LOGGER = LoggerFactory.getLogger(ClientStorage.class);

        @NonNull
        private final HostInfo hostInfo;
        @NonNull
        private final T client;
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public T get() {
            return client;
        }

        T tickAndGet() {
            final int i = counter.incrementAndGet();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Increase connection to {} to {}", hostInfo.toJson(), i);
            }
            return client;
        }

        boolean shouldClose() {
            final int i = counter.decrementAndGet();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Decrease connection to {} to {}", hostInfo.toJson(), i);
            }
            return i == 0;
        }

        int current() {
            return counter.get();
        }

    }

}
