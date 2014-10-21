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
package io.jimagezip.local;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.fabric8.common.util.Filter;
import io.fabric8.common.util.Filters;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.CurrentState;
import io.fabric8.kubernetes.api.model.ManifestContainer;
import io.fabric8.kubernetes.api.model.PodCurrentContainerInfo;
import io.fabric8.kubernetes.api.model.PodListSchema;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerListSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.ServiceListSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.hawt.util.Strings;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.UUID.randomUUID;

/**
 */
@Singleton
public class LocalNodeModel {
    private Map<String, PodSchema> podMap = new ConcurrentHashMap<>();
    private Map<String, ReplicationControllerSchema> replicationControllerMap = new ConcurrentHashMap<>();
    private Map<String, ServiceSchema> serviceMap = new ConcurrentHashMap<>();

    @Inject
    public LocalNodeModel() {
    }

    public ImmutableMap<String, PodSchema> getPodMap() {
        return ImmutableMap.copyOf(podMap);
    }


    public PodListSchema getPods() {
        PodListSchema answer = new PodListSchema();
        answer.setItems(Lists.newArrayList(podMap.values()));
        return answer;
    }

    public ImmutableList<PodSchema> getPods(Map<String, String> replicaSelector) {
        return getPods(KubernetesHelper.createPodFilter(replicaSelector));
    }

    public ImmutableList<PodSchema> getPods(Filter<PodSchema> podFilter) {
        return ImmutableList.copyOf(Filters.filter(getPodMap().values(), podFilter));
    }

    public ServiceListSchema getServices() {
        ServiceListSchema answer = new ServiceListSchema();
        answer.setItems(Lists.newArrayList(serviceMap.values()));
        return answer;
    }

    public ReplicationControllerListSchema getReplicationControllers() {
        ReplicationControllerListSchema answer = new ReplicationControllerListSchema();
        answer.setItems(Lists.newArrayList(replicationControllerMap.values()));
        return answer;
    }

    public ImmutableMap<String, ReplicationControllerSchema> getReplicationControllerMap() {
        return ImmutableMap.copyOf(replicationControllerMap);
    }

    public ImmutableMap<String, ServiceSchema> getServiceMap() {
        return ImmutableMap.copyOf(serviceMap);
    }


    /**
     * Returns all the current containers and their pods
     */
    public ImmutableMap<String, PodCurrentContainer> getPodRunningContainers() {
        Map<String, PodCurrentContainer> answer = new HashMap<>();
        for (Map.Entry<String, PodSchema> entry : podMap.entrySet()) {
            String podId = entry.getKey();
            PodSchema podSchema = entry.getValue();
            Map<String, PodCurrentContainerInfo> currentContainers = KubernetesHelper.getCurrentContainers(podSchema);
            for (Map.Entry<String, PodCurrentContainerInfo> containerEntry : currentContainers.entrySet()) {
                String containerId = containerEntry.getKey();
                PodCurrentContainerInfo currentContainer = containerEntry.getValue();
                PodCurrentContainer podCurrentContainer = createPodCurrentContainer(podId, podSchema, containerId, currentContainer);
                answer.put(containerId, podCurrentContainer);
            }
        }
        return ImmutableMap.copyOf(answer);
    }

    public PodCurrentContainer createPodCurrentContainer(String podId, PodSchema podSchema, String containerId, PodCurrentContainerInfo currentContainer) {
        return new PodCurrentContainer(this, podId, podSchema, containerId, currentContainer);
    }

    public PodSchema getPod(String id) {
        return podMap.get(id);
    }

    public ReplicationControllerSchema getReplicationController(String id) {
        return replicationControllerMap.get(id);
    }

    public ServiceSchema getService(String id) {
        return serviceMap.get(id);
    }

    /**
     * Creates a new ID for the given kind
     */
    public String createID(String kind) {
        return kind + "-" + randomUUID().toString();
    }

    public void updateReplicationController(String id, ReplicationControllerSchema replicationController) {
        if (Strings.isBlank(id)) {
            id = createID("ReplicationController");
        }
        replicationControllerMap.put(id, replicationController);
    }

    public void updatePod(String id, PodSchema pod) {
        if (Strings.isBlank(id)) {
            id = createID("Pod");
        }
        // lets make sure that for each container we have a current container created
        Map<String, PodCurrentContainerInfo> info = NodeHelper.getOrCreateCurrentContainerInfo(pod);

        List<ManifestContainer> containers = KubernetesHelper.getContainers(pod);
        for (ManifestContainer container : containers) {
            String name = container.getName();
            if (Strings.isBlank(name)) {
                // lets generate a container id
                name = createID("Container");
                container.setName(name);
            }
            PodCurrentContainerInfo containerInfo = info.get(name);
            if (containerInfo == null) {
                containerInfo = new PodCurrentContainerInfo();
                info.put(name, containerInfo);
            }
        }
        podMap.put(id, pod);
    }

}
