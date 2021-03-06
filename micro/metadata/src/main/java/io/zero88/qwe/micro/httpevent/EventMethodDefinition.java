package io.zero88.qwe.micro.httpevent;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.zero88.utils.Strings;
import io.github.zero88.utils.Urls;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.zero88.qwe.dto.JsonData;
import io.zero88.qwe.dto.msg.RequestData;
import io.zero88.qwe.event.EventAction;
import io.zero88.qwe.event.EventListener;
import io.zero88.qwe.exceptions.ServiceNotFoundException;
import io.zero88.qwe.utils.PriorityUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

/**
 * It helps define a mapping between dynamic route by {@code regex path} and {@code HttpMethod} with {@code EventAction}
 * that used by specific {@code EventBus address}
 *
 * @see EventMethodMapping
 */
@Slf4j
@Getter
@FieldNameConstants
@Jacksonized
@Builder(builderClassName = "Builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class EventMethodDefinition implements JsonData {

    @EqualsAndHashCode.Include
    private final String servicePath;
    /**
     * Identify using {@link RequestData} or not. Default is {@code True}
     * <p>
     * If {@code False}, only {@code path params} and {@code body} in {@code HTTP Request} will be included and omit
     * data in {@code HTTP Request query params} and {@code HTTP Request Header}
     */
    private final boolean useRequestData;
    private final Set<EventMethodMapping> mapping;
    /**
     * Web Router order
     */
    @JsonIgnore
    private final int order;
    @JsonIgnore
    private final HttpPathRule rule;

    private EventMethodDefinition(String servicePath, boolean useRequestData, Set<EventMethodMapping> mapping) {
        this.rule = new HttpPathRule();
        this.servicePath = this.rule.createRegex(servicePath);
        if (this.servicePath.endsWith("/.+")) {
            throw new IllegalArgumentException("Service path cannot ends with capture parameter");
        }
        this.useRequestData = useRequestData;
        if (!useRequestData) {
            log.warn("HTTP Path [{}] is not using `RequestData` that will omit data in `HTTP Request Query` and " +
                     "`HTTP Request Header`", this.servicePath);
        }
        this.order = PriorityUtils.priorityOrder(this.servicePath.length());
        this.mapping = mapping;
    }

    /**
     * Create default definition with default {@link ActionMethodMapping#CRUD_MAP}.
     *
     * @param servicePath Origin service path that represents for manipulating {@code resource} in default {@code
     *                    HTTPMethod} list
     * @param paramPath   Parameter path for manipulating {@code resource}
     * @return new instance
     * @implNote {@code paramPath} will be append after {@code servicePath}. For example:
     *     <ul>
     *     <li>{@code servicePath}: {@code /client/:clientId/product}</li>
     *     <li>{@code paramPath}: {@code /:productId}</li>
     *     </ul>
     * @see #createDefault(String, String, boolean)
     */
    public static EventMethodDefinition createDefault(String servicePath, String paramPath) {
        return createDefault(servicePath, paramPath, true);
    }

    /**
     * Create default definition with default {@link ActionMethodMapping#CRUD_MAP}.
     *
     * @param servicePath    Origin service path that represents for manipulating {@code resource} in default {@code
     *                       HTTPMethod} list
     * @param paramPath      Parameter path for manipulating {@code resource}
     * @param useRequestData Whether use {@link RequestData} in parameter in {@link EventListener} or not
     * @return new instance
     * @implNote {@code paramPath} will be append after {@code servicePath}. For example:
     *     <ul>
     *     <li>{@code servicePath}: {@code /client/:clientId/product}</li>
     *     <li>{@code paramPath}: {@code /:productId}</li>
     *     </ul>
     */
    public static EventMethodDefinition createDefault(String servicePath, String paramPath, boolean useRequestData) {
        return create(servicePath, Strings.requireNotBlank(paramPath), ActionMethodMapping.CRUD_MAP, useRequestData);
    }

    /**
     * Create definition with given {@code ActionMethodMapping}.
     * <p>
     * It is appropriate to handle {@code singleton resource} with no key or {@code action job}, e.g: {@code
     * /translate}
     *
     * @param servicePath Origin service path that represents for manipulating {@code resource} or {@code action job}
     * @param mapping     Mapping between {@code EventAction} and {@code HTTPMethod}
     * @return new instance
     * @see ActionMethodMapping
     */
    public static EventMethodDefinition create(String servicePath, @NonNull ActionMethodMapping mapping) {
        return create(servicePath, mapping, true);
    }

    /**
     * Create definition with given {@code ActionMethodMapping}.
     * <p>
     * It is appropriate to handle {@code singleton resource} with no key or {@code action job}, e.g: {@code translate}
     *
     * @param servicePath    Origin service path that represents for manipulating {@code resource} or {@code action
     *                       job}
     * @param mapping        Mapping between {@code EventAction} and {@code HTTPMethod}
     * @param useRequestData Use RequestData {@code RequestData}
     * @return new instance
     * @see ActionMethodMapping
     */
    public static EventMethodDefinition create(String servicePath, @NonNull ActionMethodMapping mapping,
                                               boolean useRequestData) {
        return create(servicePath, null, mapping, useRequestData);
    }

    /**
     * Create definition with given {@code ActionMethodMapping}.
     * <p>
     * It is appropriate to handle {@code resource} with common {@code CRUD} operations
     *
     * @param servicePath Origin service path that represents for manipulating {@code resource}
     * @param paramPath   Parameter path for manipulating {@code resource}
     * @param mapping     Mapping between {@code EventAction} and {@code HTTPMethod}
     * @return new instance
     * @implNote {@code paramPath} will be append after {@code servicePath}. For example:
     *     <ul>
     *     <li>{@code servicePath}: {@code /client/:clientId/product}</li>
     *     <li>{@code paramPath}: {@code /:productId}</li>
     *     </ul>
     * @see ActionMethodMapping
     */
    public static EventMethodDefinition create(String servicePath, String paramPath,
                                               @NonNull ActionMethodMapping mapping) {
        return create(servicePath, paramPath, mapping, true);
    }

    /**
     * Create definition with given {@code ActionMethodMapping}.
     * <p>
     * It is appropriate to handle {@code resource} with common {@code CRUD} operations
     *
     * @param servicePath    Origin service path that represents for manipulating {@code resource}
     * @param paramPath      Parameter path for manipulating {@code resource}
     * @param mapping        Mapping between {@code EventAction} and {@code HTTPMethod}
     * @param useRequestData Use request data {@code RequestData}
     * @return new instance
     * @implNote {@code paramPath} will be append after {@code servicePath}. For example:
     *     <ul>
     *     <li>{@code servicePath}: {@code /client/:clientId/product}</li>
     *     <li>{@code paramPath}: {@code /:productId}</li>
     *     </ul>
     * @see ActionMethodMapping
     */
    public static EventMethodDefinition create(String servicePath, String paramPath,
                                               @NonNull ActionMethodMapping mapping, boolean useRequestData) {
        final String sPath = Urls.combinePath(Strings.requireNotBlank(servicePath));
        final String pPath = Strings.isBlank(paramPath) ? paramPath : Urls.toCapture(paramPath);
        if ((Strings.isBlank(pPath) || sPath.equals(pPath)) && mapping.hasDuplicateMethod()) {
            throw new IllegalStateException("Has duplicate HTTP method for same endpoint");
        }
        final String cPath = Strings.isBlank(pPath) ? sPath : Urls.combinePath(sPath, pPath);
        Set<EventMethodMapping> map = mapping.get()
                                             .entrySet()
                                             .stream()
                                             .map(entry -> EventMethodMapping.builder()
                                                                             .action(entry.getKey())
                                                                             .method(entry.getValue())
                                                                             .servicePath(sPath)
                                                                             .capturePath(cPath)
                                                                             .build())
                                             .collect(Collectors.toSet());
        return EventMethodDefinition.builder().servicePath(sPath).useRequestData(useRequestData).mapping(map).build();
    }

    public static EventMethodDefinition from(@NonNull JsonObject json) {
        return EventMethodDefinition.builder()
                                    .servicePath(json.getString(Fields.servicePath))
                                    .useRequestData(json.getBoolean(Fields.useRequestData, true))
                                    .mapping(json.getJsonArray(Fields.mapping, new JsonArray())
                                                 .stream()
                                                 .map(o -> JsonData.from(o, EventMethodMapping.class))
                                                 .collect(Collectors.toSet()))
                                    .build();
    }

    public Collection<EventMethodMapping> getMapping() {
        return mapping.stream()
                      .sorted((o1, o2) -> compare(o1.method(), o2.method()))
                      .sorted((o1, o2) -> compare(Strings.fallback(o1.getCapturePath(), servicePath),
                                                  Strings.fallback(o2.getCapturePath(), servicePath)))
                      .collect(Collectors.toList());
    }

    private int compare(String n1, String n2) {
        final int c = n1.length() - n2.length();
        return c == 0 ? n1.compareTo(n2) : c;
    }

    public EventAction search(String actualPath, @NonNull HttpMethod method) {
        return mapping.stream()
                      .filter(mapping -> {
                          String regex = Strings.isBlank(mapping.getRegexPath()) ? servicePath : mapping.getRegexPath();
                          return mapping.getMethod() == method && actualPath.matches(regex);
                      })
                      .map(EventMethodMapping::getAction)
                      .findFirst()
                      .orElseThrow(() -> new ServiceNotFoundException(
                          Strings.format("Unsupported HTTP method [{0}][{1}]", method, actualPath)));
    }

    public boolean test(String actualPath, EventAction action) {
        return mapping.stream().anyMatch(mapping -> {
            String regex = Strings.isBlank(mapping.getRegexPath())
                           ? rule.createRegexPathForSearch(servicePath)
                           : mapping.getRegexPath();
            return mapping.getAction() == action && actualPath.matches(regex);
        });
    }

    public boolean test(String actualPath, HttpMethod method) {
        return mapping.stream().anyMatch(mapping -> {
            String regex = Strings.isBlank(mapping.getRegexPath())
                           ? rule.createRegexPathForSearch(servicePath)
                           : mapping.getRegexPath();
            return mapping.getMethod() == method && actualPath.matches(regex);
        });
    }

    public boolean test(String actualPath) {
        return Strings.isNotBlank(actualPath) && actualPath.matches(rule.createRegexPathForSearch(servicePath));
    }

    public static class Builder {

        Boolean useRequestData = true;

        public EventMethodDefinition build() {
            return new EventMethodDefinition(this.servicePath, this.useRequestData, this.mapping);
        }

    }

}
