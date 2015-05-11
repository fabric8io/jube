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
package io.fabric8.jube.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import io.fabric8.gateway.loadbalancer.LoadBalancer;
import io.fabric8.gateway.loadbalancer.RoundRobinLoadBalancer;
import io.fabric8.jube.local.EntityListener;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.util.IntOrString;
import io.fabric8.utils.Filter;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;

/**
 * Represents a running service
 */
public class ServiceInstance implements EntityListener<Pod> {
    private static final transient Logger LOG = LoggerFactory.getLogger(ServiceInstance.class);
    private static final String HEADLESS_PORTAL_IP = "None";

    private final Service service;
    private final String id;
    private final Map<String, String> selector;
    private final Filter<Pod> filter;
    private final List<ServicePort> servicePorts = new ArrayList<>();
    private final LoadBalancer loadBalancer;
    private final Multimap<String, ContainerService> containerServices = Multimaps.synchronizedMultimap(HashMultimap.<String, ContainerService>create());

    public ServiceInstance(Service service) {
        this.service = service;
        this.id = getName(service);
        ServiceSpec spec = KubernetesHelper.getOrCreateSpec(service);
        List<ServicePort> ports = spec.getPorts();
        if (spec.getPortalIP().equals(HEADLESS_PORTAL_IP)) {
            //do nothing service is headless
        } else if (ports != null && !ports.isEmpty()) {
            for (ServicePort servicePort : ports) {
                servicePorts.add(toNamedServicePort(id, servicePort));
            }
        } else {
            throw new IllegalArgumentException("Service: " + id + " doesn't have a valid port configuration.");
        }
        this.selector = KubernetesHelper.getSelector(service);
        Objects.notNull(this.selector, "No selector for service " + id);
        if (selector.isEmpty()) {
            throw new IllegalArgumentException("Empty selector for service " + id);
        }
        this.filter = KubernetesHelper.createPodFilter(selector);

        // TODO should we use some service metadata to choose the load balancer?
        this.loadBalancer = new RoundRobinLoadBalancer();
    }

    private static ServicePort toNamedServicePort(String serviceId, ServicePort servicePort) {
        String portName = servicePort.getName();
        String protocol = servicePort.getProtocol();
        IntOrString targetPort = servicePort.getTargetPort();
        String name = !Strings.isNullOrBlank(portName) ? portName : serviceId + "-" + targetPort.toString();
        int port = KubernetesHelper.intOrStringToInteger(targetPort, "service: " + name);
        return new ServicePort(name, port, protocol, targetPort);
    }

    public List<ContainerService> getContainerServices(String name) {
        List<ContainerService> services = new ArrayList<>();
        for (ContainerService s : containerServices.values()) {
            if (s.getName().equals(name)) {
                services.add(s);
            }
        }
        return services;
    }

    @Override
    public void entityChanged(String podId, Pod pod) {
        if (filter.matches(pod)) {
            try {
                List<ContainerService> services = new ArrayList<>();
                for (ServicePort port : servicePorts) {
                    ContainerService containerService = new ContainerService(port, pod);
                    services.add(containerService);
                }
                containerServices.replaceValues(podId, services);
            } catch (Exception e) {
                LOG.info("Ignored bad pod: " + podId + ". " + e, e);
            }
        }
    }

    @Override
    public void entityDeleted(String podId) {
        containerServices.removeAll(podId);
    }

    public Filter<Pod> getFilter() {
        return filter;
    }

    public Map<String, String> getSelector() {
        return selector;
    }

    public Service getService() {
        return service;
    }

    public String getId() {
        return id;
    }

    public List<ServicePort> getPorts() {
        return servicePorts;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    @Override
    public String toString() {
        return "Service{"
                + "id='" + id + '\''
                + ", selector=" + selector
                + ", containerServices=" + containerServices.values()
                + '}';
    }

}
