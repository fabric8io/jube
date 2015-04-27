package io.fabric8.jube.zookeeper.client;

import io.fabric8.annotations.ServiceName;
import io.fabric8.utils.PasswordEncoder;
import io.fabric8.utils.Strings;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.New;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.io.IOException;

import static io.fabric8.jube.zookeeper.Constants.DEFAULT_AUTH_SCHEME;
import static io.fabric8.jube.zookeeper.Constants.DEFAULT_AUTH_USER;

public class CuratorServiceFactory {

    private static final transient Logger LOG = LoggerFactory.getLogger(CuratorServiceFactory.class);

    @Produces
    @Singleton
    public CuratorFramework create(@ServiceName("ZOOKEEPER") String url, @New CuratorConfig config) throws IOException, InterruptedException {
        LOG.info("Connecting to ZooKeeper URL: {}", url);
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(url)
                .connectionTimeoutMs(config.getConnectionTimeOut())
                .sessionTimeoutMs(config.getSessionTimeout())
                .retryPolicy(new RetryNTimes(config.getRetryMax(), config.getRetryInterval()));

        if (!Strings.isNullOrBlank(config.getPassword())) {
            byte[] auth = (DEFAULT_AUTH_USER + ":" + PasswordEncoder.decode(config.getPassword())).getBytes();
            builder = builder.authorization(DEFAULT_AUTH_SCHEME, auth);
        }
        CuratorFramework curatorFramework = builder.build();
        curatorFramework.start();
        return curatorFramework;
    }

    public void close(@Disposes CuratorFramework curatorFramework) {
        curatorFramework.close();
    }
}
