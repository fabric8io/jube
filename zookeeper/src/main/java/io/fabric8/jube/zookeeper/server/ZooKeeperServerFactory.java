package io.fabric8.jube.zookeeper.server;

import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ServerStats;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ZooKeeperServerFactory implements Closeable {
    
    static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperServerFactory.class);

    private Closeable closeable;
    private String zooKeeperUrl;

    public ZooKeeperServerFactory(QuorumPeerConfig config) throws IOException, InterruptedException {
        LOGGER.info("Creating zookeeper server with: {}", config);
        ServerConfig serverConfig = getServerConfig(config);
        ZooKeeperServer zkServer = new ZooKeeperServer();
        FileTxnSnapLog ftxn = new FileTxnSnapLog(new File(serverConfig.getDataLogDir()), new File(serverConfig.getDataDir()));
        zkServer.setTxnLogFactory(ftxn);
        zkServer.setTickTime(serverConfig.getTickTime());
        zkServer.setMinSessionTimeout(serverConfig.getMinSessionTimeout());
        zkServer.setMaxSessionTimeout(serverConfig.getMaxSessionTimeout());
        NIOServerCnxnFactory cnxnFactory = new NIOServerCnxnFactory() {
            protected void configureSaslLogin() throws IOException {
            }
        };
        InetSocketAddress clientPortAddress = serverConfig.getClientPortAddress();
        cnxnFactory.configure(clientPortAddress, serverConfig.getMaxClientCnxns());
        updateZooKeeperURL(cnxnFactory.getLocalAddress(), cnxnFactory.getLocalPort());

        try {
            LOGGER.debug("Starting ZooKeeper server on address %s", serverConfig.getClientPortAddress());
            cnxnFactory.startup(zkServer);
            LOGGER.debug("Started ZooKeeper server");
        } catch (Exception e) {
            LOGGER.warn(String.format("Failed to start ZooKeeper server, reason : %s", e));
            cnxnFactory.shutdown();
            throw e;
        }
    }

    private void updateZooKeeperURL(InetSocketAddress localAddress, int localPort) {
        LOGGER.debug("localAddress: {} localPost: {}", localAddress, localPort);
        if (localAddress != null) {
            InetAddress address = localAddress.getAddress();
            String hostName;
            if (address != null) {
                hostName = address.getHostName();
            } else {
                hostName = localAddress.getHostName();
            }
            //hostName = "localhost";
            zooKeeperUrl = hostName + ":" + localPort;
            LOGGER.info("ZooKeeper URL to local ensemble is: " + zooKeeperUrl);
        } else {
            throw new IllegalStateException("No zookeeper URL can be found for the ensemble server!");
        }
    }

    public void close() throws IOException {
        LOGGER.info("Destroying zookeeper server: {}", closeable);
        if (closeable != null) {
            closeable.close();
            closeable = null;
        }
    }

    private ServerConfig getServerConfig(QuorumPeerConfig peerConfig) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.readFrom(peerConfig);
        LOGGER.info("Created zookeeper server configuration: {}", serverConfig);
        return serverConfig;
    }

    public String getZooKeeperUrl() {
        return zooKeeperUrl;
    }


    static class SimpleServer implements Closeable, ServerStats.Provider {
        private final ZooKeeperServer server;
        private final NIOServerCnxnFactory cnxnFactory;

        SimpleServer(ZooKeeperServer server, NIOServerCnxnFactory cnxnFactory) {
            this.server = server;
            this.cnxnFactory = cnxnFactory;
        }

        @Override
        public void close() throws IOException {
            cnxnFactory.shutdown();
            try {
                cnxnFactory.join();
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
            if (server.getZKDatabase() != null) {
                // see https://issues.apache.org/jira/browse/ZOOKEEPER-1459
                server.getZKDatabase().close();
            }
        }

        @Override
        public long getOutstandingRequests() {
            return server.getOutstandingRequests();
        }

        @Override
        public long getLastProcessedZxid() {
            return server.getLastProcessedZxid();
        }

        @Override
        public String getState() {
            return server.getState();
        }

        @Override
        public int getNumAliveConnections() {
            return server.getNumAliveConnections();
        }
    }
}
