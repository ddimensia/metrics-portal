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
package models.ebean;

import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Data Model for SQL storage of report schedules.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@DiscriminatorValue("ONE_OFF")
public class OneOffReportSchedule extends ReportSchedule {

    /**
     * Convert this schedule to its internal representation.
     *
     * @return the internal representation of this schedule.
     */
    public Schedule toInternal() {
        return new OneOffSchedule.Builder()
                .setRunAtAndAfter(getRunAt())
                .setRunUntil(getRunUntil())
                .build();
    }
}
// CHECKSTYLE.ON: MemberNameCheck