/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.jboss.jube.proxy;

import io.fabric8.gateway.loadbalancer.LoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.streams.Pump;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * A handler for a single service proxy
 */
public class ServiceProxyHandler implements Handler<NetSocket> {
    private static final transient Logger LOG = LoggerFactory.getLogger(ServiceProxyHandler.class);

    private final Vertx vertx;
    private final Service service;
    private final LoadBalancer loadBalancer;

    public ServiceProxyHandler(Vertx vertx, Service service, LoadBalancer loadBalancer) {
        this.vertx = vertx;
        this.service = service;
        this.loadBalancer = loadBalancer;
    }

    @Override
    public void handle(final NetSocket socket) {
        NetClient client = null;
        TcpClientRequestFacade requestFacade = new TcpClientRequestFacade(socket);
        List<ContainerService> services = service.getContainerServices();
        if (!services.isEmpty()) {
            ContainerService containerService = loadBalancer.choose(services, requestFacade);
            if (containerService != null) {
                URI uri = containerService.getURI();
                System.out.println("Binding the inbound socket requeset to container service: " + containerService);
                try {
                    Handler<AsyncResult<NetSocket>> handler = new Handler<AsyncResult<NetSocket>>() {
                        public void handle(final AsyncResult<NetSocket> asyncSocket) {
                            NetSocket clientSocket = asyncSocket.result();
                            Pump.createPump(clientSocket, socket).start();
                            Pump.createPump(socket, clientSocket).start();
                        }
                    };
                    client = createClient(socket, uri, handler);
                } catch (MalformedURLException e) {
                    LOG.warn("Failed to parse URL: " + containerService + ". " + e, e);
                }
            }
        }
        if (client == null) {
            // fail to route
            LOG.info("No service implementation available for " + service);
            socket.close();
        }
    }

    /**
     * Creates a new client for the given URL and handler
     */
    protected NetClient createClient(NetSocket socket, URI url, Handler<AsyncResult<NetSocket>> handler) throws MalformedURLException {
        NetClient client = vertx.createNetClient();
        int port = url.getPort();
        String host = url.getHost();
        LOG.info("Connecting " + socket.remoteAddress() + " to host " + host + " port " + port + " service " + service);
        return client.connect(port, host, handler);
    }
}
