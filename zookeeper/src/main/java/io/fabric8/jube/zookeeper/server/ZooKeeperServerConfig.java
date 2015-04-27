package io.fabric8.jube.zookeeper.server;

import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

import javax.inject.Inject;
import java.net.InetSocketAddress;

public class ZooKeeperServerConfig extends QuorumPeerConfig {
    /**
     * @param clientPort   the port the ZooKeeper client uses to connect to
     * @param dataDir      the directory ZooKeeper uses to store its data
     * @param dataLogDir   the directory ZooKeeper uses to store its logs
     * @param tickTime     the keep alive tick time
     * @param initLimit    initialisation limit
     * @param syncLimit    synchronisation limit
     */
    @Inject
    public ZooKeeperServerConfig(@ConfigProperty(name = "ZOOKEEPER_CLIENT_PORT", defaultValue = "2181")
                                 int clientPort,
                                 @ConfigProperty(name = "ZOOKEEPER_DATADIR", defaultValue = "ensemble/data")
                                 String dataDir,
                                 @ConfigProperty(name = "ZOOKEEPER_DATA_LOG_DIR", defaultValue = "ensemble/log")
                                 String dataLogDir,
                                 @ConfigProperty(name = "ZOOKEEPER_TICKTIME", defaultValue = "" + ZooKeeperServer.DEFAULT_TICK_TIME)
                                 int tickTime,
                                 @ConfigProperty(name = "ZOOKEEPER_INIT_LIMIT")
                                 int initLimit,
                                 @ConfigProperty(name = "ZOOKEEPER_SYNC_LIMIT")
                                 int syncLimit) {
        this.dataDir = dataDir;
        this.dataLogDir = dataLogDir;
        this.syncLimit = syncLimit;
        this.initLimit = initLimit;
        if (clientPort > 0) {
            this.clientPortAddress = new InetSocketAddress(clientPort);
        }
    }
}
