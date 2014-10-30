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
import io.fabric8.utils.Filter;
import io.fabric8.utils.Strings;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.PodCurrentContainerInfo;
import io.fabric8.kubernetes.api.model.PodListSchema;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerListSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.ServiceListSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.fabric8.zookeeper.ZkPath;
import io.fabric8.zookeeper.utils.ZooKeeperUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.jboss.jube.KubernetesModel;
import org.jboss.jube.local.EntityListener;
import org.jboss.jube.local.EntityListenerList;
import org.jboss.jube.local.LocalKubernetesModel;
import org.jboss.jube.local.NodeHelper;
import org.jboss.jube.local.PodCurrentContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mirrors ZooKeeper data into a local in memory model and all updates to the model are written directly to ZooKeeper
 */
public class ApiMasterKubernetesModel implements KubernetesModel {
    private static final transient Logger LOG = LoggerFactory.getLogger(ApiMasterKubernetesModel.class);

    private final LocalKubernetesModel memoryModel = new LocalKubernetesModel();
    private final CuratorFramework curator;
    private final ExecutorService treeCacheExecutor = Executors.newSingleThreadExecutor();

    private final PathChildrenCacheListener treeListener = new PathChildrenCacheListener() {
        @Override
        public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent event) throws Exception {
            treeCacheEvent(event);
        }
    };

    private final TreeCache treeCache;
    private final String zkPath;

    private final EntityListenerList<PodSchema> podListeners = new EntityListenerList<>();
    private final EntityListenerList<ReplicationControllerSchema> replicationControllerListeners = new EntityListenerList<>();
    private final EntityListenerList<ServiceSchema> serviceListeners = new EntityListenerList<>();


    @Singleton
    @Inject
    public ApiMasterKubernetesModel(CuratorFramework curator) throws Exception {
        this.curator = curator;
        zkPath = ZkPath.KUBERNETES_MODEL.getPath();
        treeCache = new TreeCache(curator, zkPath, true, false, true, treeCacheExecutor);
        treeCache.start(TreeCache.StartMode.NORMAL);
        treeCache.getListenable().addListener(treeListener);

    }

    // Add and remove listeners
    //-------------------------------------------------------------------------

    public void addPodListener(EntityListener<PodSchema> listener) {
        podListeners.addListener(listener);
    }

    public void removePodListener(EntityListener<PodSchema> listener) {
        podListeners.removeListener(listener);
    }

    public void addReplicationControllerListener(EntityListener<ReplicationControllerSchema> listener) {
        replicationControllerListeners.addListener(listener);
    }

    public void removeReplicationControllerListener(EntityListener<ReplicationControllerSchema> listener) {
        replicationControllerListeners.removeListener(listener);
    }

    public void addServiceListener(EntityListener<ServiceSchema> listener) {
        serviceListeners.addListener(listener);
    }

    public void removeServiceListener(EntityListener<ServiceSchema> listener) {
        serviceListeners.removeListener(listener);
    }


    // Updating API which just writes to ZK and waits for ZK watches to update in memory
    // -------------------------------------------------------------------------
    @Override
    public PodSchema deletePod(String podId) {
        PodSchema answer = memoryModel.deletePod(podId);
        deleteEntity(zkPathForPod(podId));
        return answer;
    }


    @Override
    public void updatePod(String id, PodSchema entity) {
        writeEntity(zkPathForPod(id), entity);
            // memoryModel.updatePod(id, entity);
    }


    @Override
    public boolean updatePodIfNotExist(String id, PodSchema entity) {
        if (memoryModel.updatePodIfNotExist(id, entity)) {
            // lets not write it yet - we're just doing this to set a unique ID
            return true;
        }
        return false;
    }

    @Override
    public void updateService(String id, ServiceSchema entity) {
        writeEntity(zkPathForService(id), entity);

        // memoryModel.updateService(id, entity);
    }

    @Override
    public void deleteService(String id) {
        deleteEntity(zkPathForService(id));

        //memoryModel.deleteService(id);
    }

    @Override
    public void updateReplicationController(String id, ReplicationControllerSchema entity) {
        writeEntity(zkPathForReplicationController(id), entity);
        //memoryModel.updateReplicationController(id, entity);
    }

    @Override
    public void deleteReplicationController(String id) {
        deleteEntity(zkPathForReplicationController(id));

        // memoryModel.deleteReplicationController(id);
    }


    // Reading API from memory
    // -------------------------------------------------------------------------

    @Override
    public String getOrCreateId(String id, String kind) {
        return memoryModel.getOrCreateId(id, kind);
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
    public String createID(String kind) {
        return memoryModel.createID(kind);
    }


    // Implementation methods
    //-------------------------------------------------------------------------
    protected String zkPathForPod(String id) {
        return zkPath + "/" + NodeHelper.KIND_POD + "-" + id;
    }

    protected String zkPathForService(String id) {
        return zkPath + "/" + NodeHelper.KIND_SERVICE + "-" + id;
    }

    protected String zkPathForReplicationController(String id) {
        return zkPath + "/" + NodeHelper.KIND_REPLICATION_CONTROLLER + "-" + id;
    }




    protected void writeEntity(String path, Object entity) {
        try {
            String json = KubernetesHelper.toJson(entity);
            System.out.println("Writing to path: " + path + " json: " + json);
            ZooKeeperUtils.setData(curator, path, json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update object at path: " + path + ". " + e, e);
        }
    }

    protected void deleteEntity(String path) {
        try {
            ZooKeeperUtils.delete(curator, path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object at path: " + path + ". " + e, e);
        }
    }



    protected void treeCacheEvent(PathChildrenCacheEvent event) {
        ChildData childData = event.getData();
        if (childData == null) {
            return;
        }
        String path = childData.getPath();
        PathChildrenCacheEvent.Type type = event.getType();
        byte[] data = childData.getData();
        if (data == null || data.length == 0 || path == null) {
            return;
        }
        if (path.startsWith(zkPath)) {
            path = path.substring(zkPath.length());
        }
        boolean remove = false;
        switch (type) {
            case CHILD_ADDED:
            case CHILD_UPDATED:
                break;
            case CHILD_REMOVED:
                remove = true;
                break;
            default:
                return;
        }
        try {
            Object dto = KubernetesHelper.loadJson(data);
            updateLocalModel(dto, remove);
        } catch (Exception e) {
            LOG.warn("Failed to parse the JSON: " + new String(data) + ". Reason: " + e, e);
        }
    }

    protected void updateLocalModel(Object dto, boolean remove) {
        if (dto instanceof PodSchema) {
            podChanged((PodSchema) dto, remove);
        } else if (dto instanceof ReplicationControllerSchema) {
            replicationControllerChanged((ReplicationControllerSchema) dto, remove);
        } else if (dto instanceof ServiceSchema) {
            serviceChanged((ServiceSchema) dto, remove);
        } else {
            System.out.println("Unrecognised DTO: " + dto);
        }
    }

    protected void podChanged(PodSchema entity, boolean remove) {
        if (remove) {
            String id = entity.getId();
            if (Strings.isNotBlank(id)) {
                memoryModel.deletePod(id);
                podListeners.entityDeleted(id);

            }
        } else {
            String id = memoryModel.getOrCreateId(entity.getId(), NodeHelper.KIND_POD);
            memoryModel.updatePod(id, entity);
            podListeners.entityChanged(id, entity);
        }
    }

    protected void replicationControllerChanged(ReplicationControllerSchema entity, boolean remove) {
        if (remove) {
            String id = entity.getId();
            if (Strings.isNotBlank(id)) {
                memoryModel.deleteReplicationController(id);
                replicationControllerListeners.entityDeleted(id);
            }
        } else {
            String id = memoryModel.getOrCreateId(entity.getId(), NodeHelper.KIND_REPLICATION_CONTROLLER);
            memoryModel.updateReplicationController(id, entity);
            replicationControllerListeners.entityChanged(id, entity);
        }
    }

    protected void serviceChanged(ServiceSchema entity, boolean remove) {
        if (remove) {
            String id = entity.getId();
            if (Strings.isNotBlank(id)) {
                memoryModel.deleteService(id);
                serviceListeners.entityDeleted(id);
            }
        } else {
            String id = memoryModel.getOrCreateId(entity.getId(), NodeHelper.KIND_SERVICE);
            memoryModel.updateService(id, entity);
            serviceListeners.entityChanged(id, entity);
        }
    }

}
