/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.jube.proxy;

import io.fabric8.common.util.Filter;
import io.fabric8.common.util.Objects;
import io.fabric8.gateway.loadbalancer.LoadBalancer;
import io.fabric8.gateway.loadbalancer.RandomLoadBalancer;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.hawt.util.Strings;
import org.jboss.jube.local.EntityListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a running service
 */
public class Service implements EntityListener<PodSchema> {
    private static final transient Logger LOG = LoggerFactory.getLogger(Service.class);

    private final ServiceSchema service;
    private final String id;
    private final Map<String, String> selector;
    private final Filter<PodSchema> filter;
    private final int port;
    private final int containerPort;
    private final LoadBalancer loadBalancer;
    private final Map<String, ContainerService> containerServices = new ConcurrentHashMap<>();

    public Service(ServiceSchema service) {
        this.service = service;
        this.id = service.getId();
        Integer portInt = service.getPort();
        Objects.notNull(portInt, "port for service " + id);
        this.port = portInt.intValue();
        if (this.port <= 0) {
            throw new IllegalArgumentException("Invalid port number " + this.port + " for service " + id);
        }
        IntOrString containerPort = service.getContainerPort();
        Objects.notNull(containerPort, "containerPort for service " + id);
        String containerPortText = containerPort.toString();
        if (Strings.isBlank(containerPortText)) {
            throw new IllegalArgumentException("No containerPort for service " + id);
        }
        try {
            this.containerPort = Integer.parseInt(containerPortText);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid containerPort expression " + containerPortText + " for service " + id + ". " + e, e);
        }
        if (this.containerPort <= 0) {
            throw new IllegalArgumentException("Invalid containerPort number " + this.containerPort + " for service " + id);
        }
        this.selector = service.getSelector();
        Objects.notNull(this.selector, "No selector for service " + id);
        if (selector.isEmpty()) {
            throw new IllegalArgumentException("Empty selector for service " + id);
        }
        this.filter = KubernetesHelper.createPodFilter(selector);

        // TODO should we use some service metadata to choose the load balancer?
        this.loadBalancer = new RandomLoadBalancer();
    }

    public List<ContainerService> getContainerServices() {
        return new ArrayList<ContainerService>(containerServices.values());
    }

    @Override
    public void entityChanged(String podId, PodSchema pod) {
        if (filter.matches(pod)) {
            try {
                ContainerService containerService = new ContainerService(this, pod);
                containerServices.put(podId, containerService);
            } catch (Exception e) {
                LOG.info("Ignored bad pod: " + podId + ". " + e, e);
            }
        }
    }

    @Override
    public void entityDeleted(String podId) {
        containerServices.remove(podId);
    }

    public Filter<PodSchema> getFilter() {
        return filter;
    }

    public Map<String, String> getSelector() {
        return selector;
    }

    public ServiceSchema getService() {
        return service;
    }

    public String getId() {
        return id;
    }

    public int getPort() {
        return port;
    }

    public int getContainerPort() {
        return containerPort;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }
}
