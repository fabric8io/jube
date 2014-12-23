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
package io.fabric8.jube.local;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.UUID.randomUUID;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.fabric8.jube.KubernetesModel;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.utils.Filter;
import io.fabric8.utils.Filters;
import io.hawt.util.Strings;

/**
 * A pure in memory implementation of the {@link KubernetesModel}
 */
public class LocalKubernetesModel implements KubernetesModel {
    private ConcurrentHashMap<String, Pod> podMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ReplicationController> replicationControllerMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Service> serviceMap = new ConcurrentHashMap<>();

    public LocalKubernetesModel() {
    }

    // Pods
    //-------------------------------------------------------------------------

    @Override
    public ImmutableMap<String, Pod> getPodMap() {
        return ImmutableMap.copyOf(podMap);
    }

    @Override
    public PodList getPods() {
        PodList answer = new PodList();
        answer.setItems(Lists.newArrayList(podMap.values()));
        return answer;
    }

    @Override
    public ImmutableList<Pod> getPods(Map<String, String> replicaSelector) {
        return getPods(KubernetesHelper.createPodFilter(replicaSelector));
    }

    @Override
    public ImmutableList<Pod> getPods(Filter<Pod> podFilter) {
        return ImmutableList.copyOf(Filters.filter(getPodMap().values(), podFilter));
    }

    @Override
    public Pod getPod(String id) {
        return podMap.get(id);
    }

    @Override
    public void updatePod(String id, Pod pod) {
        id = getOrCreateId(id, NodeHelper.KIND_POD);

        // lets make sure that for each container we have a current container created
        Map<String, ContainerStatus> info = NodeHelper.getOrCreateCurrentContainerInfo(pod);

        List<Container> containers = KubernetesHelper.getContainers(pod);
        for (Container container : containers) {
            String name = getOrCreateId(container.getName(), NodeHelper.KIND_POD);
            container.setName(name);
            ContainerStatus containerInfo = info.get(name);
            if (containerInfo == null) {
                containerInfo = new ContainerStatus();
                info.put(name, containerInfo);
            }
        }
        podMap.put(id, pod);
    }

    @Override
    public String getOrCreateId(String id, String kind) {
        if (Strings.isBlank(id)) {
            id = createID(kind);
        }
        return id;
    }

    /**
     * Updates the pod if one does not already exist
     */
    @Override
    public boolean updatePodIfNotExist(String id, Pod pod) {
        Pod oldValue = podMap.putIfAbsent(id, pod);
        return oldValue == null;
    }

    /**
     * Removes the pods from the model.
     * <p/>
     * <b>Note</b> you should make sure to delete any container processes too!
     */
    @Override
    public Pod deletePod(String podId) {
        if (Strings.isBlank(podId)) {
            return null;
        }
        return podMap.remove(podId);
    }

    /**
     * Returns all the current containers and their pods
     */
    @Override
    public ImmutableMap<String, PodCurrentContainer> getPodRunningContainers(KubernetesModel model) {
        Map<String, PodCurrentContainer> answer = new HashMap<>();
        for (Map.Entry<String, Pod> entry : podMap.entrySet()) {
            String podId = entry.getKey();
            Pod podSchema = entry.getValue();
            Map<String, ContainerStatus> currentContainers = KubernetesHelper.getCurrentContainers(podSchema);
            for (Map.Entry<String, ContainerStatus> containerEntry : currentContainers.entrySet()) {
                String containerId = containerEntry.getKey();
                ContainerStatus currentContainer = containerEntry.getValue();
                PodCurrentContainer podCurrentContainer = new PodCurrentContainer(model, podId, podSchema, containerId, currentContainer);
                answer.put(containerId, podCurrentContainer);
            }
        }
        return ImmutableMap.copyOf(answer);
    }


    // Replication Controllers
    //-------------------------------------------------------------------------

    @Override
    public ReplicationController getReplicationController(String id) {
        return replicationControllerMap.get(id);
    }

    @Override
    public ReplicationControllerList getReplicationControllers() {
        ReplicationControllerList answer = new ReplicationControllerList();
        answer.setItems(Lists.newArrayList(replicationControllerMap.values()));
        return answer;
    }

    @Override
    public ImmutableMap<String, ReplicationController> getReplicationControllerMap() {
        return ImmutableMap.copyOf(replicationControllerMap);
    }

    @Override
    public void updateReplicationController(String id, ReplicationController replicationController) {
        id = getOrCreateId(id, NodeHelper.KIND_REPLICATION_CONTROLLER);
        replicationControllerMap.put(id, replicationController);
    }

    @Override
    public void deleteReplicationController(String controllerId) {
        replicationControllerMap.remove(controllerId);
        System.out.println("Deleted replicationController " + controllerId + ". Now has " + replicationControllerMap.size() + " service(s)");
    }


    // Services
    //-------------------------------------------------------------------------

    @Override
    public ServiceList getServices() {
        ServiceList answer = new ServiceList();
        answer.setItems(Lists.newArrayList(serviceMap.values()));
        return answer;
    }

    @Override
    public Service getService(String id) {
        return serviceMap.get(id);
    }

    @Override
    public ImmutableMap<String, Service> getServiceMap() {
        return ImmutableMap.copyOf(serviceMap);
    }

    @Override
    public void updateService(String id, Service entity) {
        id = getOrCreateId(id, NodeHelper.KIND_SERVICE);
        serviceMap.put(id, entity);
    }

    @Override
    public void deleteService(String serviceId) {
        serviceMap.remove(serviceId);
        System.out.println("Deleted service " + serviceId + ". Now has " + serviceMap.size() + " service(s)");

    }

    // Other stuff
    //-------------------------------------------------------------------------

    /**
     * Creates a new ID for the given kind
     */
    @Override
    public String createID(String kind) {
        return kind + "-" + randomUUID().toString();
    }

}
