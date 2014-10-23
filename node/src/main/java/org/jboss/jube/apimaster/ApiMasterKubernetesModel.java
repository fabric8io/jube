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
package org.jboss.jube.apimaster;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.fabric8.common.util.Filter;
import io.fabric8.kubernetes.api.model.PodCurrentContainerInfo;
import io.fabric8.kubernetes.api.model.PodListSchema;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerListSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.ServiceListSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import org.apache.curator.framework.CuratorFramework;
import org.jboss.jube.KubernetesModel;
import org.jboss.jube.local.LocalKubernetesModel;
import org.jboss.jube.local.PodCurrentContainer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Mirrors ZooKeeper data into a local in memory model and all updates to the model are written directly to ZooKeeper
 */
public class ApiMasterKubernetesModel implements KubernetesModel {
    private final KubernetesModel memoryModel = new LocalKubernetesModel();
    private final CuratorFramework curator;

    @Singleton
    @Inject
    public ApiMasterKubernetesModel(CuratorFramework curator) {
        this.curator = curator;
    }

    @Override
    public ImmutableMap<String, PodSchema> getPodMap() {
        return memoryModel.getPodMap();
    }

    @Override
    public PodListSchema getPods() {
        return memoryModel.getPods();
    }

    @Override
    public ImmutableList<PodSchema> getPods(Map<String, String> replicaSelector) {
        return memoryModel.getPods(replicaSelector);
    }

    @Override
    public ImmutableList<PodSchema> getPods(Filter<PodSchema> podFilter) {
        return memoryModel.getPods(podFilter);
    }

    @Override
    public PodSchema getPod(String id) {
        return memoryModel.getPod(id);
    }

    @Override
    public void updatePod(String id, PodSchema pod) {
        memoryModel.updatePod(id, pod);
    }

    @Override
    public boolean updatePodIfNotExist(String id, PodSchema pod) {
        return memoryModel.updatePodIfNotExist(id, pod);
    }

    @Override
    public PodSchema deletePod(String podId) {
        return memoryModel.deletePod(podId);
    }

    @Override
    public ImmutableMap<String, PodCurrentContainer> getPodRunningContainers() {
        return memoryModel.getPodRunningContainers();
    }

    @Override
    public PodCurrentContainer createPodCurrentContainer(String podId, PodSchema podSchema, String containerId, PodCurrentContainerInfo currentContainer) {
        return memoryModel.createPodCurrentContainer(podId, podSchema, containerId, currentContainer);
    }

    @Override
    public ReplicationControllerSchema getReplicationController(String id) {
        return memoryModel.getReplicationController(id);
    }

    @Override
    public ReplicationControllerListSchema getReplicationControllers() {
        return memoryModel.getReplicationControllers();
    }

    @Override
    public ImmutableMap<String, ReplicationControllerSchema> getReplicationControllerMap() {
        return memoryModel.getReplicationControllerMap();
    }

    @Override
    public void updateReplicationController(String id, ReplicationControllerSchema replicationController) {
        memoryModel.updateReplicationController(id, replicationController);
    }

    @Override
    public void deleteReplicationController(String controllerId) {
        memoryModel.deleteReplicationController(controllerId);
    }

    @Override
    public ServiceListSchema getServices() {
        return memoryModel.getServices();
    }

    @Override
    public ServiceSchema getService(String id) {
        return memoryModel.getService(id);
    }

    @Override
    public ImmutableMap<String, ServiceSchema> getServiceMap() {
        return memoryModel.getServiceMap();
    }

    @Override
    public void updateService(String id, ServiceSchema entity) {
        memoryModel.updateService(id, entity);
    }

    @Override
    public void deleteService(String serviceId) {
        memoryModel.deleteService(serviceId);
    }

    @Override
    public String createID(String kind) {
        return memoryModel.createID(kind);
    }
}
