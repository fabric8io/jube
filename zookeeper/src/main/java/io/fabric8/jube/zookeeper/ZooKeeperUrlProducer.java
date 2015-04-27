package io.fabric8.jube.zookeeper;

import com.google.common.base.Strings;
import io.fabric8.annotations.ServiceName;
import io.fabric8.jube.zookeeper.server.ZooKeeperServerConfig;
import io.fabric8.jube.zookeeper.server.ZooKeeperServerFactory;
import io.fabric8.utils.Systems;

import javax.enterprise.inject.New;
import javax.enterprise.inject.Produces;
import java.io.IOException;

public class ZooKeeperUrlProducer {

    @Produces
    @ServiceName("ZOOKEEPER")
    public String create(@New ZooKeeperServerConfig serverConfig) throws IOException, InterruptedException {
        String providedUrl = Systems.getEnvVarOrSystemProperty("ZOOKEEPER_URL", "");
        if (Strings.isNullOrEmpty(providedUrl)) {
            ZooKeeperServerFactory factory =  new ZooKeeperServerFactory(serverConfig);
            return factory.getZooKeeperUrl();
        } else {
            return providedUrl;
        }
    }
}
