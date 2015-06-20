/**
 *  Copyright 2005-2015 Red Hat, Inc.
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
import io.fabric8.kubernetes.api.model.ContainerState;
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
    public static final String DEFAULT_NAMESPACE = "default";
    private ConcurrentHashMap<String, NamespaceModel> namespaces = new ConcurrentHashMap<>();
    private String namespace = DEFAULT_NAMESPACE;

    public LocalKubernetesModel() {
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the default namespace
     */
    @Override
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    // Pods
    //-------------------------------------------------------------------------

    @Override
    public ImmutableMap<String, Pod> getPodMap() {
        return getPodMap(namespace);
    }

    public ImmutableMap<String, Pod> getPodMap(String namespace) {
        return ImmutableMap.copyOf(getInternalPodMap(namespace));
    }

    @Override
    public PodList getPods() {
        return getPods(namespace);
    }

    @Override
    public PodList getPods(String namespace) {
        PodList answer = new PodList();
        answer.setItems(Lists.newArrayList(getInternalPodMap(namespace).values()));
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
        return getInternalPodMap(namespace).get(id);
    }

    @Override
    public Pod getPod(String id, String namespace) {
        return getInternalPodMap(namespace).get(id);
    }

    @Override
    public void updatePod(String id, Pod pod) {
        String namespace = defaultNamespace(KubernetesHelper.getNamespace(pod));
        id = getOrCreateId(id, NodeHelper.KIND_POD);

        // lets make sure that for each container we have a current container created
        List<Container> containers = KubernetesHelper.getContainers(pod);
        for (Container container : containers) {
            String name =  getOrCreateId(container.getName(), NodeHelper.KIND_POD);
            ContainerState containerState = NodeHelper.getOrCreateContainerState(pod, name);
        }
        getInternalPodMap(namespace).put(id, pod);
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
        Pod oldValue = getInternalPodMap(namespace).putIfAbsent(id, pod);
        return oldValue == null;
    }

    /**
     * Removes the pods from the model.
     * <p/>
     * <b>Note</b> you should make sure to delete any container processes too!
     */
    @Override
    public Pod deletePod(String podId, String namespace) {
        namespace = defaultNamespace(namespace);
        if (Strings.isBlank(podId)) {
            return null;
        }
        return getInternalPodMap(namespace).remove(podId);
    }

    /**
     * Returns all the current containers and their pods
     */
    @Override
    public ImmutableMap<String, PodCurrentContainer> getPodRunningContainers(KubernetesModel model) {
        Map<String, PodCurrentContainer> answer = new HashMap<>();
        for (Map.Entry<String, Pod> entry : getInternalPodMap(namespace).entrySet()) {
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
        return getReplicationController(id, namespace);
    }

    @Override
    public ReplicationController getReplicationController(String id, String namespace) {
        return getInternalReplicationControllerMap(namespace).get(id);
    }

    @Override
    public ReplicationControllerList getReplicationControllers() {
        return getReplicationControllers(namespace);
    }

    @Override
    public ReplicationControllerList getReplicationControllers(String namespace) {
        ReplicationControllerList answer = new ReplicationControllerList();
        answer.setItems(Lists.newArrayList(getInternalReplicationControllerMap(namespace).values()));
        return answer;
    }

    @Override
    public ImmutableMap<String, ReplicationController> getReplicationControllerMap() {
        return ImmutableMap.copyOf(getInternalReplicationControllerMap(namespace));
    }

    @Override
    public void updateReplicationController(String id, ReplicationController replicationController) {
        id = getOrCreateId(id, NodeHelper.KIND_REPLICATION_CONTROLLER);
        String namespace = defaultNamespace(KubernetesHelper.getNamespace(replicationController));
        getInternalReplicationControllerMap(namespace).put(id, replicationController);
    }

    @Override
    public void deleteReplicationController(String controllerId, String namespace) {
        namespace = defaultNamespace(namespace);
        getInternalReplicationControllerMap(namespace).remove(controllerId);
        System.out.println("Deleted replicationController " + controllerId + ". Now has " + getInternalReplicationControllerMap(namespace).size() + " service(s)");
    }


    // Services
    //-------------------------------------------------------------------------

    @Override
    public ServiceList getServices() {
        return getServices(namespace);
    }

    @Override
    public ServiceList getServices(String namespace) {
        ServiceList answer = new ServiceList();
        answer.setItems(Lists.newArrayList(getInternalServiceMap(namespace).values()));
        return answer;
    }

    @Override
    public Service getService(String id) {
        return getService(id, namespace);
    }

    @Override
    public Service getService(String id, String namespace) {
        return getInternalServiceMap(namespace).get(id);
    }

    @Override
    public ImmutableMap<String, Service> getServiceMap() {
        return ImmutableMap.copyOf(getInternalServiceMap(namespace));
    }

    @Override
    public void updateService(String id, Service entity) {
        String namespace = defaultNamespace(KubernetesHelper.getNamespace(entity));
        id = getOrCreateId(id, NodeHelper.KIND_SERVICE);
        getInternalServiceMap(namespace).put(id, entity);
    }

    @Override
    public void deleteService(String serviceId, String namespace) {
        namespace = defaultNamespace(namespace);
        getInternalServiceMap(namespace).remove(serviceId);
        System.out.println("Deleted service " + serviceId + ". Now has " + getInternalServiceMap(namespace).size() + " service(s)");

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

    protected ConcurrentHashMap<String, Pod> getInternalPodMap(String namespace) {
        return namespaceModel(namespace).podMap;
    }

                 
    protected ConcurrentHashMap<String, ReplicationController> getInternalReplicationControllerMap(String namespace) {
        return namespaceModel(namespace).replicationControllerMap;
    }

    protected ConcurrentHashMap<String, Service> getInternalServiceMap(String namespace) {
        return namespaceModel(namespace).serviceMap;
    }

    protected class NamespaceModel {
        public ConcurrentHashMap<String, Pod> podMap = new ConcurrentHashMap<>();
        public ConcurrentHashMap<String, ReplicationController> replicationControllerMap = new ConcurrentHashMap<>();
        public ConcurrentHashMap<String, Service> serviceMap = new ConcurrentHashMap<>();

    }
    
    
    /**
     * Returns the namespace model for the given namespace id
     */
    protected NamespaceModel namespaceModel(String namespace) {
        namespace = defaultNamespace(namespace);
        NamespaceModel answer = namespaces.get(namespace);
        if (answer == null) {
            answer = new NamespaceModel();
            namespaces.put(namespace ,answer);
        }
        return answer;
    }

    protected String defaultNamespace(String namespace) {
        if (Strings.isBlank(namespace)) {
            namespace = this.namespace;
        }
        if (Strings.isBlank(namespace)) {
            namespace = DEFAULT_NAMESPACE;
        }
        return namespace;
    }

}
