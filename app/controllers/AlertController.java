/*
 * Copyright 2015 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package controllers;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.Context;
import models.internal.NagiosExtension;
import models.internal.Operator;
import models.internal.Quantity;
import models.internal.QueryResult;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultQuantity;
import models.view.PagedContainer;
import models.view.Pagination;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Metrics portal alert controller. Exposes APIs to query and manipulate alerts.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@Singleton
public class AlertController extends Controller {

    /**
     * Public constructor.
     *
     * @param configuration Instance of Play's {@link Config}.
     * @param alertRepository Instance of {@link AlertRepository}.
     * @param organizationRepository Instance of {@link OrganizationRepository}.
     */
    @Inject
    public AlertController(
            final Config configuration,
            final AlertRepository alertRepository,
            final OrganizationRepository organizationRepository) {
        this(configuration.getInt("alerts.limit"), alertRepository, organizationRepository);
    }

    /**
     * Adds an alert in the alert repository.
     *
     * @return Ok if the alert was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result addOrUpdate() {
        final Alert alert;
        try {
            final models.view.Alert viewAlert = buildViewAlert(request().body());
            alert = convertToInternalAlert(viewAlert);
        } catch (final IOException e) {
            LOGGER.error()
                    .setMessage("Failed to build an alert.")
                    .setThrowable(e)
                    .log();
            return badRequest("Invalid request body.");
        }

        try {
            _alertRepository.addOrUpdateAlert(alert, _organizationRepository.get(request()));
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to add an alert.")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return noContent();
    }

    /**
     * Query for alerts.
     *
     * @param contains The text to search for. Optional.
     * @param context The context of the alert. Optional.
     * @param cluster The cluster of the statistic to evaluate as part of the alert. Optional.
     * @param service The service of the statistic to evaluate as part of the alert. Optional.
     * @param limit The maximum number of results to return. Optional.
     * @param offset The number of results to skip. Optional.
     * @return {@link Result} paginated matching alerts.
     */
    // CHECKSTYLE.OFF: ParameterNameCheck - Names must match query parameters.
    public Result query(
            @Nullable final String contains,
            @Nullable final String context,
            @Nullable final String cluster,
            @Nullable final String service,
            @Nullable final Integer limit,
            @Nullable final Integer offset) {
        // CHECKSTYLE.ON: ParameterNameCheck

        // Convert and validate parameters
        final Optional<String> argContains = Optional.ofNullable(contains);
        final Context contextValue;
        try {
            contextValue = context == null ? null : Context.valueOf(context);
        } catch (final IllegalArgumentException iae) {
            return badRequest("Invalid context argument");
        }
        final Optional<Context> argContext = Optional.ofNullable(contextValue);
        final Optional<String> argCluster = Optional.ofNullable(cluster);
        final Optional<String> argService = Optional.ofNullable(service);
        final Optional<Integer> argOffset = Optional.ofNullable(offset);
        final int argLimit = Math.min(_maxLimit, MoreObjects.firstNonNull(limit, _maxLimit));
        if (argLimit < 0) {
            return badRequest("Invalid limit; must be greater than or equal to 0");
        }
        if (argOffset.isPresent() && argOffset.get() < 0) {
            return badRequest("Invalid offset; must be greater than or equal to 0");
        }

        // Build conditions map
        final Map<String, String> conditions = Maps.newHashMap();
        argContains.ifPresent(v -> conditions.put("contains", v));
        argContext.ifPresent(v -> conditions.put("context", v.toString()));
        argCluster.ifPresent(v -> conditions.put("cluster", v));
        argService.ifPresent(v -> conditions.put("service", v));

        // Build a host repository query
        final AlertQuery query = _alertRepository.createAlertQuery(_organizationRepository.get(request()))
                .contains(argContains)
                .context(argContext)
                .service(argService)
                .cluster(argCluster)
                .limit(argLimit)
                .offset(argOffset);

        // Execute the query
        final QueryResult<Alert> result;
        try {
            result = query.execute();
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Alert query failed")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }

        // Wrap the query results and return as JSON
        if (result.etag().isPresent()) {
            response().setHeader(HttpHeaders.ETAG, result.etag().get());
        }
        return ok(Json.toJson(new PagedContainer<>(
                result.values()
                        .stream()
                        .map(this::internalModelToViewModel)
                        .collect(Collectors.toList()),
                new Pagination(
                        request().path(),
                        result.total(),
                        result.values().size(),
                        argLimit,
                        argOffset,
                        conditions))));
    }

    /**
     * Get specific alert.
     *
     * @param id The identifier of the alert.
     * @return Matching alert.
     */
    public Result get(final String id) {
        final UUID identifier;
        try {
            identifier = UUID.fromString(id);
        } catch (final IllegalArgumentException e) {
            return badRequest();
        }
        final Optional<Alert> result = _alertRepository.getAlert(identifier, _organizationRepository.get(request()));
        if (!result.isPresent()) {
            return notFound();
        }
        // Return as JSON
        return ok(Json.toJson(result.get()));
    }

    /**
     * Delete a specific alert.
     *
     * @param id The identifier of the alert.
     * @return No content
     */
    public Result delete(final String id) {
        final UUID identifier = UUID.fromString(id);
        final int deleted = _alertRepository.deleteAlert(identifier, _organizationRepository.get(request()));
        if (deleted > 0) {
            return noContent();
        } else {
            return notFound();
        }
    }

    private models.view.Alert internalModelToViewModel(final Alert alert) {
        final models.view.Alert viewAlert = new models.view.Alert();
        viewAlert.setCluster(alert.getCluster());
        viewAlert.setContext(alert.getContext().toString());
        viewAlert.setExtensions(mergeExtensions(alert.getNagiosExtension()));
        viewAlert.setId(alert.getId().toString());
        viewAlert.setMetric(alert.getMetric());
        viewAlert.setName(alert.getName());
        viewAlert.setOperator(alert.getOperator().toString());
        viewAlert.setPeriod(alert.getPeriod().toString());
        viewAlert.setService(alert.getService());
        viewAlert.setStatistic(alert.getStatistic());
        final models.view.Quantity viewValue = new models.view.Quantity();
        viewValue.setValue(alert.getValue().getValue());
        if (alert.getValue().getUnit().isPresent()) {
            viewValue.setUnit(alert.getValue().getUnit().get());
        }
        viewAlert.setValue(viewValue);
        return viewAlert;
    }

    private Quantity convertToInternalQuantity(final models.view.Quantity viewQuantity) {
        return new DefaultQuantity.Builder()
                .setUnit(viewQuantity.getUnit())
                .setValue(viewQuantity.getValue())
                .build();
    }

    private Optional<NagiosExtension> convertToInternalNagiosExtension(final Map<String, Object> extensionsMap) {
        try {
            return Optional.of(
                    OBJECT_MAPPER
                            .convertValue(extensionsMap, NagiosExtension.Builder.class)
                            .build());
            // CHECKSTYLE.OFF: IllegalCatch - Assume there is no Nagios data on build failure.
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            return Optional.empty();
        }
    }

    private Alert convertToInternalAlert(final models.view.Alert viewAlert) throws IOException {
        try {
            final DefaultAlert.Builder alertBuilder = new DefaultAlert.Builder()
                    .setCluster(viewAlert.getCluster())
                    .setMetric(viewAlert.getMetric())
                    .setName(viewAlert.getName())
                    .setService(viewAlert.getService())
                    .setStatistic(viewAlert.getStatistic());
            if (viewAlert.getValue() != null) {
                alertBuilder.setValue(convertToInternalQuantity(viewAlert.getValue()));
            }
            if (viewAlert.getId() != null) {
                alertBuilder.setId(UUID.fromString(viewAlert.getId()));
            }
            if (viewAlert.getContext() != null) {
                alertBuilder.setContext(Context.valueOf(viewAlert.getContext()));
            }
            if (viewAlert.getOperator() != null) {
                alertBuilder.setOperator(Operator.valueOf(viewAlert.getOperator()));
            }
            if (viewAlert.getPeriod() != null) {
                alertBuilder.setPeriod(Duration.parse(viewAlert.getPeriod()));
            }
            if (viewAlert.getExtensions() != null) {
                alertBuilder.setNagiosExtension(convertToInternalNagiosExtension(viewAlert.getExtensions()).orElse(null));
            }
            return alertBuilder.build();
            // CHECKSTYLE.OFF: IllegalCatch - Translate any failure to bad input.
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatch
            throw new IOException(e);
        }
    }

    private ImmutableMap<String, Object> mergeExtensions(@Nullable final NagiosExtension nagiosExtension) {
        final ImmutableMap.Builder<String, Object> nagiosMapBuilder = ImmutableMap.builder();
        if (nagiosExtension != null) {
            nagiosMapBuilder.put(NAGIOS_EXTENSION_SEVERITY_KEY, nagiosExtension.getSeverity());
            nagiosMapBuilder.put(NAGIOS_EXTENSION_NOTIFY_KEY, nagiosExtension.getNotify());
            nagiosMapBuilder.put(NAGIOS_EXTENSION_MAX_CHECK_ATTEMPTS_KEY, nagiosExtension.getMaxCheckAttempts());
            nagiosMapBuilder.put(NAGIOS_EXTENSION_FRESHNESS_THRESHOLD_KEY, nagiosExtension.getFreshnessThreshold().getSeconds());
        }
        return nagiosMapBuilder.build();
    }

    private models.view.Alert buildViewAlert(final Http.RequestBody body) throws IOException {
        final JsonNode jsonBody = body.asJson();
        if (jsonBody == null) {
            throw new IOException();
        }
        return OBJECT_MAPPER.readValue(jsonBody.toString(), models.view.Alert.class);
    }

    private AlertController(
            final int maxLimit,
            final AlertRepository alertRepository,
            final OrganizationRepository organizationRepository) {
        _maxLimit = maxLimit;
        _alertRepository = alertRepository;
        _organizationRepository = organizationRepository;
    }

    private final int _maxLimit;
    private final AlertRepository _alertRepository;
    private final OrganizationRepository _organizationRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertController.class);
    private static final String NAGIOS_EXTENSION_SEVERITY_KEY = "severity";
    private static final String NAGIOS_EXTENSION_NOTIFY_KEY = "notify";
    private static final String NAGIOS_EXTENSION_MAX_CHECK_ATTEMPTS_KEY = "max_check_attempts";
    private static final String NAGIOS_EXTENSION_FRESHNESS_THRESHOLD_KEY = "freshness_threshold";
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
