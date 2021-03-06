package io.zero88.qwe.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.vertx.core.Vertx;
import io.zero88.qwe.SharedDataLocalProxy;

/**
 * This annotation is used to inject {@code EventBus context} into method parameter.
 * <p>
 * The context is one of {@code EventAction}, {@code Vert.x}, {@code SharedDataLocalProxy}, or {@code EventBusClient}
 *
 * @see Vertx
 * @see SharedDataLocalProxy
 * @see EventBusClient
 * @see EventAction
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface EBContext {}
