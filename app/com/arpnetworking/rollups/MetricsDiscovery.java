/*
 * Copyright 2019 Dropbox Inc.
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
package com.arpnetworking.rollups;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.pattern.PatternsCS;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.typesafe.config.Config;
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.FiniteDuration;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Actor for discovering the list of metrics available to be rolled up on a periodic basis.
 *
 * This actor maintains an internal set of metric names and acts as a source for downstream
 * actors that perform the actual rollups.  This is intended to be used as a singleton in the
 * cluster.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class MetricsDiscovery extends AbstractActorWithTimers {

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(
                        FETCH_MSG,
                        work -> {
                            getTimers().startSingleTimer(REFRESH_TIMER, FETCH_MSG, _fetchInterval);
                            _refreshDeadline = _fetchInterval.fromNow();
                            fetchMetricsForRollup();
                        })
                .match(KairosMetricNamesQueryResponse.class, this::updateMetricsSet)
                .match(
                        Status.Failure.class,
                        failure -> LOGGER.warn("Failed to get metrics from Kairos", failure.cause()))
                .match(
                        MetricFetch.class,
                        work -> {
                            final Optional<String> metricName = getNextMetric();
                            if (metricName.isPresent()) {
                                getSender().tell(metricName.get(), getSelf());
                            } else {
                                getSender().tell(new NoMoreMetrics(_refreshDeadline), getSelf());
                            }
                        })
                .build();
    }

    /**
     * Metrics discovery constructor.
     *
     * @param configuration play configuration object
     * @param kairosDbClient client to use for fetching metric names
     */
    @Inject
    public MetricsDiscovery(final Config configuration, final KairosDbClient kairosDbClient) {
        _fetchInterval = ConfigurationHelper.getFiniteDuration(configuration, "rollup.fetch.interval");
        _kairosDbClient = kairosDbClient;
        _metricsSet = new LinkedHashSet<>();
        _setIterator = _metricsSet.iterator();
        _refreshDeadline = Deadline.now();
        getSelf().tell(FETCH_MSG, ActorRef.noSender());
    }

    private void fetchMetricsForRollup() {
        PatternsCS.pipe(_kairosDbClient.queryMetricNames(), getContext().dispatcher())
                .to(getSelf());
    }

    private void updateMetricsSet(final KairosMetricNamesQueryResponse response) {
        response.getResults().stream()
                .filter(ROLLUP_METRIC_RE.asPredicate().negate())
                .forEach(_metricsSet::add);
        _setIterator = _metricsSet.iterator();
    }

    private Optional<String> getNextMetric() {
        final String next;
        if (_setIterator.hasNext()) {
            next = _setIterator.next();
            _setIterator.remove();
        } else {
            next = null;
        }

        return Optional.ofNullable(next);
    }

    private final FiniteDuration _fetchInterval;
    private final KairosDbClient _kairosDbClient;
    private final Set<String> _metricsSet;
    private Iterator<String> _setIterator;
    private Deadline _refreshDeadline;
    private static final String REFRESH_TIMER = "refresh_timer";
    private static final Object FETCH_MSG = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsDiscovery.class);
    private static final Pattern ROLLUP_METRIC_RE = Pattern.compile("^.*_1[hd]$");
}
