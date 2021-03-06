/*
 * Copyright 2019 Dropbox, Inc.
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
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.metrics.portal.reports.ReportQuery;
import com.arpnetworking.metrics.portal.reports.ReportRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.reports.Report;
import models.view.PagedContainer;
import models.view.Pagination;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Metrics portal report controller. Exposes APIs to query and manipulate reports.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 */
@Singleton
public class ReportController extends Controller {

    /**
     * Public constructor.
     *
     * @param configuration Instance of Play's {@code Config}.
     * @param reportRepository Instance of {@link ReportRepository}.
     * @param organizationRepository Instance of {@link OrganizationRepository}.
     */
    @Inject
    public ReportController(
            final Config configuration,
            final ReportRepository reportRepository,
            final OrganizationRepository organizationRepository) {
        this(configuration.getInt("reports.limit"), reportRepository, organizationRepository);
    }

    /**
     * Updates a report within the report repository, or creates one if it doesn't already exist.
     *
     * @return Ok if the report was added or updated successfully, an HTTP error code otherwise.
     */
    public Result addOrUpdate() {
        final Report report;
        try {
            final JsonNode body = request().body().asJson();
            report = OBJECT_MAPPER.treeToValue(body, models.view.reports.Report.class).toInternal();
        } catch (final JsonProcessingException e) {
            LOGGER.error()
                    .setMessage("Failed to build a report.")
                    .setThrowable(e)
                    .log();
            return badRequest("Invalid request body.");
        }

        try {
            _reportRepository.addOrUpdateReport(report, _organizationRepository.get(request()));
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to add or update a report.")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return noContent();
    }

    /**
     * Query for reports.
     *
     * @param limit The maximum number of results to return. Optional.
     * @param offset The number of results to skip. Optional.
     * @return {@link Result} paginated matching reports.
     */
    // CHECKSTYLE.OFF: ParameterNameCheck - Names must match query parameters.
    public Result query(
            @Nullable final Integer limit,
            @Nullable final Integer offset) {
        // CHECKSTYLE.ON: ParameterNameCheck

        final Organization organization;
        try {
            organization = _organizationRepository.get(request());
        } catch (final NoSuchElementException e) {
            return internalServerError();
        }

        // Convert and validate parameters
        final int argLimit = Optional.ofNullable(limit).map(l -> Math.min(l, _maxLimit)).orElse(_maxLimit);
        if (argLimit < 0) {
            return badRequest("Invalid limit; must be greater than or equal to 0");
        }

        final Optional<Integer> argOffset = Optional.ofNullable(offset);
        if (argOffset.isPresent() && argOffset.get() < 0) {
            return badRequest("Invalid offset; must be greater than or equal to 0");
        }

        final ReportQuery query = _reportRepository.createReportQuery(organization)
                .limit(argLimit)
                .offset(argOffset.orElse(0));

        final QueryResult<Report> result;
        try {
            result = query.execute();
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Report query failed")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }

        final Map<String, String> conditions = ImmutableMap.of();

        result.etag().ifPresent(etag -> response().setHeader(HttpHeaders.ETAG, etag));
        return ok(Json.toJson(new PagedContainer<>(
                result.values()
                        .stream()
                        .map(models.view.reports.Report::fromInternal)
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
     * Get specific report.
     *
     * @param id The identifier of the report.
     * @return The report, if any, otherwise notFound.
     */
    public Result get(final UUID id) {
        final Organization organization;
        try {
            organization = _organizationRepository.get(request());
        } catch (final NoSuchElementException e) {
            return internalServerError();
        }
        final Optional<Report> report = _reportRepository.getReport(id, organization);
        return report
                .map(r -> ok(Json.toJson(models.view.reports.Report.fromInternal(r))))
                .orElseGet(ReportController::notFound);
    }

    /**
     * Delete a specific report.
     *
     * @param id The identifier of the report.
     * @return No content if successful, otherwise an HTTP error code.
     */
    public Result delete(final UUID id) {
        final Organization organization = _organizationRepository.get(request());
        final int deletedCount = _reportRepository.deleteReport(id, organization);
        if (deletedCount == 0) {
            return notFound();
        }
        return noContent();
    }

    private ReportController(
            final int maxLimit,
            final ReportRepository reportRepository,
            final OrganizationRepository organizationRepository) {
        _maxLimit = maxLimit;
        _reportRepository = reportRepository;
        _organizationRepository = organizationRepository;
    }

    private final int _maxLimit;
    private final ReportRepository _reportRepository;
    private final OrganizationRepository _organizationRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportController.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
