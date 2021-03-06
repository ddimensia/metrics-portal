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
package models.view.impl;

import models.internal.scheduling.Period;
import models.view.scheduling.Schedule;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import javax.annotation.Nullable;

/**
 * Schedule for a job that repeats periodically.
 * <p>
 * Play view models are mutable.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PeriodicSchedule extends Schedule {

    public Instant getRunUntil() {
        return _runUntil;
    }

    public void setRunUntil(@Nullable final Instant runUntil) {
        this._runUntil = runUntil;
    }

    public Period getPeriod() {
        return _period;
    }

    public void setPeriod(final Period period) {
        _period = period;
    }

    public ZoneId getZone() {
        return _zone;
    }

    public void setZone(final ZoneId zone) {
        _zone = zone;
    }

    public Duration getOffset() {
        return _offset;
    }

    public void setOffset(final Duration offset) {
        _offset = offset;
    }

    @Override
    public com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule toInternal() {
        return new com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule.Builder()
                .setRunAtAndAfter(getRunAtAndAfter())
                .setRunUntil(_runUntil)
                .setZone(_zone)
                .setPeriod(_period.toChronoUnit())
                .setOffset(_offset)
                .build();
    }

    /**
     * Create a {@code PeriodicSchedule} from its internal representation.
     *
     * @param schedule The internal model.
     * @return The view model.
     */
    public static PeriodicSchedule fromInternal(final com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule schedule) {
        final PeriodicSchedule viewSchedule = new PeriodicSchedule();
        viewSchedule.setRunAtAndAfter(schedule.getRunAtAndAfter());
        viewSchedule.setRunUntil(schedule.getRunUntil().orElse(null));
        final Period period = Period.fromChronoUnit(schedule.getPeriod());
        viewSchedule.setPeriod(period);
        viewSchedule.setOffset(schedule.getOffset());
        viewSchedule.setZone(schedule.getZone());
        return viewSchedule;
    }

    private Instant _runUntil;
    private Period _period;
    private Duration _offset;
    private ZoneId _zone;
}
