package io.fabric8.jube.zookeeper.client;

import org.apache.deltaspike.core.api.config.ConfigProperty;

import javax.inject.Inject;

import static io.fabric8.jube.zookeeper.Constants.DEFAULT_CONNECTION_TIMEOUT_MS;
import static io.fabric8.jube.zookeeper.Constants.DEFAULT_RETRY_INTERVAL;
import static io.fabric8.jube.zookeeper.Constants.DEFAULT_SESSION_TIMEOUT_MS;
import static io.fabric8.jube.zookeeper.Constants.DEFAULT_MAX_RETRIES_LIMIT;

public class CuratorConfig {


    private String user;
    private String password;
    private int retryMax = Integer.parseInt(DEFAULT_MAX_RETRIES_LIMIT);
    private int retryInterval = Integer.parseInt(DEFAULT_RETRY_INTERVAL);
    private int connectionTimeOut = Integer.parseInt(DEFAULT_CONNECTION_TIMEOUT_MS);
    private int sessionTimeout = Integer.parseInt(DEFAULT_SESSION_TIMEOUT_MS);

    @Inject
    public CuratorConfig(
            @ConfigProperty(name = "ZOOKEEPER_USER")
            String user,
            @ConfigProperty(name = "ZOOKEEPER_PASSWORD")
            String password,
            @ConfigProperty(name = "ZOOKEEPER_RETRY_POLICY_MAX_RETRIES", defaultValue = DEFAULT_MAX_RETRIES_LIMIT)
            int retryMax,
            @ConfigProperty(name = "ZOOKEEPER_RETRY_POLICY_INTERVAL_MS", defaultValue = DEFAULT_RETRY_INTERVAL)
            int retryInterval,
            @ConfigProperty(name = "ZOOKEEPER_CONNECTION_TIMEOUT", defaultValue = DEFAULT_CONNECTION_TIMEOUT_MS)
            int connectionTimeOut,
            @ConfigProperty(name = "ZOOKEEPER_SESSION_TIMEOUT", defaultValue = DEFAULT_SESSION_TIMEOUT_MS)
            int sessionTimeout) {
        this.user = user;
        this.password = password;
        this.retryMax = retryMax;
        this.retryInterval = retryInterval;
        this.connectionTimeOut = connectionTimeOut;
        this.sessionTimeout = sessionTimeout;
    }

    @Override
    public String toString() {
        return "CuratorConfig{" +
                ", user='" + user + '\'' +
                ", password='" + password + '\'' +
                ", retryMax=" + retryMax +
                ", retryInterval=" + retryInterval +
                ", connectionTimeOut=" + connectionTimeOut +
                ", sessionTimeout=" + sessionTimeout +
                '}';
    }


    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public int getRetryMax() {
        return retryMax;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    public int getConnectionTimeOut() {
        return connectionTimeOut;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CuratorConfig that = (CuratorConfig) o;

        if (connectionTimeOut != that.connectionTimeOut) return false;
        if (retryInterval != that.retryInterval) return false;
        if (retryMax != that.retryMax) return false;
        if (sessionTimeout != that.sessionTimeout) return false;
        if (password != null ? !password.equals(that.password) : that.password != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = password != null ? password.hashCode() : 0;
        result = 31 * result + retryMax;
        result = 31 * result + retryInterval;
        result = 31 * result + connectionTimeOut;
        result = 31 * result + sessionTimeout;
        return result;
    }
}
