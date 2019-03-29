package com.arpnetworking.utility;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;

/**
 * Guice module that provides a way to bind a scoped/subset config to allow more dynamic config loading.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public final class ConfigurationOverrideModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(Config.class).annotatedWith(Assisted.class).toInstance(_config);
    }

    public ConfigurationOverrideModule(final Config config) {
        _config = config;
    }

    private final Config _config;
}
