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

package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import models.internal.reports.ReportSource;
import net.sf.oval.constraint.AssertURL;
import net.sf.oval.constraint.AssertURLCheck;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal model for a report source that pulls screenshots from a Chrome web browser.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class ChromeScreenshotReportSource implements ReportSource {
    private final UUID _id;
    private final URI _uri;
    private final String _title;
    private final boolean _ignoreCertificateErrors;
    private final String _triggeringEventName;

    private ChromeScreenshotReportSource(final Builder builder) {
        _id = builder._id;
        _uri = builder._uri;
        _title = builder._title;
        _ignoreCertificateErrors = builder._ignoreCertificateErrors;
        _triggeringEventName = builder._triggeringEventName;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", _id)
                .add("url", _uri)
                .add("title", _title)
                .add("ignoreCertificateErrors", _ignoreCertificateErrors)
                .add("triggeringEventName", _triggeringEventName)
                .toString();
    }

    @Override
    public UUID getId() {
        return _id;
    }

    /**
     * Get the URI for this report source.
     *
     * @return the URI for this report source.
     */
    public URI getUri() {
        return _uri;
    }

    /**
     * Get the title for this report source.
     *
     * @return the title for this report source.
     */
    public String getTitle() {
        return _title;
    }

    /**
     * Returns {@code true} if this source ignores certificate errors.
     *
     * @return {@code true} if this source ignores certificate errors, otherwise {@code false}.
     */
    public boolean ignoresCertificateErrors() {
        return _ignoreCertificateErrors;
    }

    /**
     * Get the browser event name used to trigger this report source.
     *
     * The report will be considered generated once an event with this name is registered
     * with the browser.
     *
     * @return the name of the triggering event.
     */
    public String getTriggeringEventName() {
        return _triggeringEventName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ChromeScreenshotReportSource that = (ChromeScreenshotReportSource) o;
        return _ignoreCertificateErrors == that._ignoreCertificateErrors
                && Objects.equals(_id, that._id)
                && Objects.equals(_uri, that._uri)
                && Objects.equals(_title, that._title)
                && Objects.equals(_triggeringEventName, that._triggeringEventName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id, _uri, _title, _ignoreCertificateErrors, _triggeringEventName);
    }

    /**
     * Builder implementation that constructs {@link ChromeScreenshotReportSource}.
     */
    public static final class Builder extends OvalBuilder<ChromeScreenshotReportSource> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(ChromeScreenshotReportSource::new);
        }

        /**
         * The source id. Required. Cannot be null or empty.
         *
         * @param id The id.
         * @return This instance of {@code Builder}
         */
        public Builder setId(final UUID id) {
            _id = id;
            return this;
        }

        /**
         * The source URL. Required. Cannot be null or empty.
         *
         * @param uri The URL.
         * @return This instance of {@code Builder}
         */
        public Builder setUri(final URI uri) {
            _uri = uri;
            return this;
        }

        /**
         * The source title. Required. Cannot be null or empty.
         *
         * @param title The title.
         * @return This instance of {@code Builder}
         */
        public Builder setTitle(final String title) {
            _title = title;
            return this;
        }

        /**
         * The triggering event name for the source. Required. Cannot be null or empty.
         *
         * @param triggeringEventName The event name.
         * @return This instance of {@code Builder}
         */
        public Builder setTriggeringEventName(final String triggeringEventName) {
            _triggeringEventName = triggeringEventName;
            return this;
        }

        /**
         * Determine if the source should ignore certificate errors. Cannot be null. Defaults to false.
         *
         * @param ignoreCertificateErrors Whether this source should ignore certificate errors.
         * @return This instance of {@code Builder}
         */
        public Builder setIgnoreCertificateErrors(final Boolean ignoreCertificateErrors) {
            _ignoreCertificateErrors = ignoreCertificateErrors;
            return this;
        }

        @NotNull
        private UUID _id;
        @NotNull
        @AssertURL(permittedURISchemes = {AssertURLCheck.URIScheme.HTTP, AssertURLCheck.URIScheme.HTTPS})
        private URI _uri;
        @NotNull
        @NotEmpty
        private String _title;
        @NotNull
        @NotEmpty
        private String _triggeringEventName;
        @NotNull
        private Boolean _ignoreCertificateErrors = false;

    }
}
