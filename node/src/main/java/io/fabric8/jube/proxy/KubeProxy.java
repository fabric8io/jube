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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.fabric8.gateway.loadbalancer.LoadBalancer;
import io.fabric8.jube.ServiceIDs;
import io.fabric8.jube.apimaster.ApiMasterKubernetesModel;
import io.fabric8.jube.local.EntityListener;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.impl.DefaultVertxFactory;

/**
 * Manages instances of {@link ServiceProxy} for each service that gets created or destroyed
 */
public class KubeProxy {
    private final Vertx vertx;
    private final ApiMasterKubernetesModel model;
    private Map<String, ServiceProxy> serviceMap = new ConcurrentHashMap<>();
    private Set<String> ignoredServiceIDs = new HashSet<>(Arrays.asList(ServiceIDs.KUBERNETES_RO_SERVICE_ID, ServiceIDs.KUBERNETES_SERVICE_ID, ServiceIDs.FABRIC8_CONSOLE_SERVICE_ID));

    @Singleton
    @Inject
    public KubeProxy(ApiMasterKubernetesModel model) {
        this.model = model;
        this.vertx = DefaultVertxFactory.newVertx();

        model.addPodListener(new EntityListener<Pod>() {
            @Override
            public void entityChanged(String id, Pod entity) {
                Collection<ServiceProxy> services = getServices();
                for (ServiceProxy service : services) {
                    service.entityChanged(id, entity);
                }
            }

            @Override
            public void entityDeleted(String id, Pod entity) {
                Collection<ServiceProxy> services = getServices();
                for (ServiceProxy service : services) {
                    service.entityDeleted(id, entity);
                }
            }
        });

        model.addServiceListener(new EntityListener<Service>() {
            @Override
            public void entityChanged(String id, Service entity) {
                serviceChanged(id, entity);
            }

            @Override
            public void entityDeleted(String id, Service entity) {
                serviceDeleted(id);
            }
        });
    }

    protected synchronized void serviceChanged(String id, Service serviceEntity) {
        if (ignoredServiceIDs.contains(id)) {
            return;
        }

        // lets delete the old service as we may have changed the port or selector
        serviceDeleted(id);

        ServiceInstance service = new ServiceInstance(serviceEntity);
        for (ServicePort servicePort : service.getPorts()) {
            LoadBalancer loadBalancer = service.getLoadBalancer();
            ServiceProxy serviceProxy = new ServiceProxy(vertx, service, servicePort, loadBalancer);
            serviceProxy.init();
            serviceMap.put(id, serviceProxy);
        }


        // now lets populate it with the current pods
        ImmutableMap<String, Pod> podMap = model.getPodMap();
        ImmutableSet<Map.Entry<String, Pod>> entries = podMap.entrySet();
        for (Map.Entry<String, Pod> entry : entries) {
            String podId = entry.getKey();
            Pod pod = entry.getValue();
            service.entityChanged(podId, pod);
        }
        System.out.println("Service now initialised as: " + service);
    }

    protected void serviceDeleted(String id) {
        ServiceProxy service = serviceMap.remove(id);
        if (service != null) {
            service.destroy();
        }
    }

    protected Collection<ServiceProxy> getServices() {
        return serviceMap.values();
    }
}
