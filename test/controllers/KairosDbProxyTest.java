package controllers;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.typesafe.config.ConfigFactory;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

/**
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class KairosDbProxyTest extends WithApplication {
    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .loadConfig(ConfigFactory.load("portal.application.conf"))
                .configure("proxy.filterRollups", true)
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }
}
