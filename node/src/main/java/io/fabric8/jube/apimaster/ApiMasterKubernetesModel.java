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
package io.fabric8.jube.apimaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.fabric8.jube.KubernetesModel;
import io.fabric8.jube.local.EntityListener;
import io.fabric8.jube.local.EntityListenerList;
import io.fabric8.jube.local.LocalKubernetesModel;
import io.fabric8.jube.local.NodeHelper;
import io.fabric8.jube.local.PodCurrentContainer;
import io.fabric8.jube.model.HostNode;
import io.fabric8.jube.model.HostNodeModel;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.utils.Filter;
import io.fabric8.utils.Strings;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;

/**
 * Mirrors ZooKeeper data into a local in memory model and all updates to the model are written directly to ZooKeeper
 */
@Singleton
public class ApiMasterKubernetesModel implements KubernetesModel {
    
    private static final transient Logger LOG = LoggerFactory.getLogger(ApiMasterKubernetesModel.class);
    private static final String KUBERNETES_MODEL = "/kubernetes/model";
    private final LocalKubernetesModel memoryModel = new LocalKubernetesModel();
    private final CuratorFramework curator;
    private final HostNodeModel hostNodeModel;
    private final TreeCacheListener treeListener = new TreeCacheListener() {
        @Override
        public void childEvent(CuratorFramework curatorFramework, TreeCacheEvent event) throws Exception {
            treeCacheEvent(event);
        }
    };

    private final TreeCache treeCache;

    private final EntityListenerList<Pod> podListeners = new EntityListenerList<>();
    private final EntityListenerList<ReplicationController> replicationControllerListeners = new EntityListenerList<>();
    private final EntityListenerList<Service> serviceListeners = new EntityListenerList<>();

    @Inject
    public ApiMasterKubernetesModel(CuratorFramework curator, HostNodeModel hostNodeModel) throws Exception {
        this.curator = curator;
        this.hostNodeModel = hostNodeModel;
        this.treeCache = new TreeCache(curator, KUBERNETES_MODEL);
        this.treeCache.start();
        this.treeCache.getListenable().addListener(treeListener);
    }

    // Add and remove listeners
    //-------------------------------------------------------------------------

    public void addPodListener(EntityListener<Pod> listener) {
        podListeners.addListener(listener);
    }

    public void removePodListener(EntityListener<Pod> listener) {
        podListeners.removeListener(listener);
    }

    public void addReplicationControllerListener(EntityListener<ReplicationController> listener) {
        replicationControllerListeners.addListener(listener);
    }

    public void removeReplicationControllerListener(EntityListener<ReplicationController> listener) {
        replicationControllerListeners.removeListener(listener);
    }

    public void addServiceListener(EntityListener<Service> listener) {
        serviceListeners.addListener(listener);
    }

    public void removeServiceListener(EntityListener<Service> listener) {
        serviceListeners.removeListener(listener);
    }

    // Updating API which just writes to ZK and waits for ZK watches to update in memory
    // -------------------------------------------------------------------------
    @Override
    public Pod deletePod(String podId, String namespace) {
        if (Strings.isNotBlank(podId)) {
            Pod answer = memoryModel.deletePod(podId, namespace);
            deleteEntity(zkPathForPod(podId));
            return answer;
        } else {
            return null;
        }
    }

    @Override
    public void updatePod(String id, Pod entity) {
        writeEntity(zkPathForPod(id), entity);
        // memoryModel.updatePod(id, entity);
    }

    @Override
    public boolean updatePodIfNotExist(String id, Pod entity) {
        if (memoryModel.updatePodIfNotExist(id, entity)) {
            // lets not write it yet - we're just doing this to set a unique ID
            return true;
        }
        return false;
    }

    @Override
    public void updateService(String id, Service entity) {
        writeEntity(zkPathForService(id), entity);
        // memoryModel.updateService(id, entity);
    }

    @Override
    public void deleteService(String id, String namespace) {
        deleteEntity(zkPathForService(id));
        //memoryModel.deleteService(id);
    }

    @Override
    public void updateReplicationController(String id, ReplicationController entity) {
        writeEntity(zkPathForReplicationController(id, KubernetesHelper.getNamespace(entity)), entity);
        //memoryModel.updateReplicationController(id, entity);
    }

    @Override
    public void deleteReplicationController(String id, String namespace) {
        deleteEntity(zkPathForReplicationController(id, namespace));
        // memoryModel.deleteReplicationController(id);
    }

    // Reading API from memory
    // -------------------------------------------------------------------------

    @Override
    public String getNamespace() {
        return memoryModel.getNamespace();
    }

    @Override
    public void setNamespace(String namespace) {
        memoryModel.setNamespace(namespace);
    }


    @Override
    public String getOrCreateId(String id, String kind) {
        return memoryModel.getOrCreateId(id, kind);
    }

    @Override
    public ImmutableMap<String, Pod> getPodMap() {
        return memoryModel.getPodMap();
    }

    @Override
    public PodList getPods() {
        return memoryModel.getPods();
    }

    @Override
    public ImmutableList<Pod> getPods(Map<String, String> replicaSelector) {
        return memoryModel.getPods(replicaSelector);
    }

    @Override
    public ImmutableList<Pod> getPods(Filter<Pod> podFilter) {
        return memoryModel.getPods(podFilter);
    }

    @Override
    public Pod getPod(String id) {
        return memoryModel.getPod(id);
    }

    @Override
    public PodList getPods(String namespace) {
        return memoryModel.getPods(namespace);
    }

    @Override
    public Pod getPod(String id, String namespace) {
        return memoryModel.getPod(id, namespace);
    }

    @Override
    public ImmutableMap<String, PodCurrentContainer> getPodRunningContainers(KubernetesModel model) {
        return memoryModel.getPodRunningContainers(model);
    }


    @Override
    public ReplicationController getReplicationController(String id) {
        return memoryModel.getReplicationController(id);
    }

    @Override
    public ReplicationControllerList getReplicationControllers() {
        return memoryModel.getReplicationControllers();
    }

    @Override
    public ImmutableMap<String, ReplicationController> getReplicationControllerMap() {
        return memoryModel.getReplicationControllerMap();
    }

    @Override
    public ReplicationController getReplicationController(String id, String namespace) {
        return memoryModel.getReplicationController(id, namespace);
    }

    @Override
    public ReplicationControllerList getReplicationControllers(String namespace) {
        return memoryModel.getReplicationControllers(namespace);
    }


    @Override
    public ServiceList getServices() {
        return memoryModel.getServices();
    }

    @Override
    public Service getService(String id) {
        return memoryModel.getService(id);
    }

    @Override
    public ServiceList getServices(String namespace) {
        return memoryModel.getServices(namespace);
    }

    @Override
    public Service getService(String id, String namespace) {
        return memoryModel.getService(id, namespace);
    }

    @Override
    public ImmutableMap<String, Service> getServiceMap() {
        return memoryModel.getServiceMap();
    }

    @Override
    public String createID(String kind) {
        return memoryModel.createID(kind);
    }

    // Load balancing API
    //-------------------------------------------------------------------------

    public String remoteCreatePod(Pod pod) {
        Exception failed = null;
        List<HostNode> hosts = new ArrayList<>(hostNodeModel.getMap().values());

        // lets try randomize the list
        int size = hosts.size();
        if (size <= 0) {
            throw new IllegalStateException("No host nodes available");
        }
        if (size == 1) {
            HostNode hostNode = hosts.get(0);
            try {
                return tryCreatePod(hostNode, pod);
            } catch (Exception e) {
                failed = e;
                LOG.error("Failed to create pod: " + getName(pod) + " on host: " + hostNode + ". " + e, e);
            }
        } else {
            Collections.shuffle(hosts);
            for (HostNode hostNode : hosts) {
                try {
                    return tryCreatePod(hostNode, pod);
                } catch (Exception e) {
                    failed = e;
                    LOG.error("Failed to create pod: " + getName(pod) + " on host: " + hostNode + ". " + e, e);
                }
            }
        }
        NodeHelper.setPodTerminated(pod, failed);
        return null;
    }

    protected String tryCreatePod(HostNode hostNode, Pod pod) throws Exception {
        LOG.info("Attempting to create pod on host: " + hostNode.getWebUrl());
        KubernetesExtensionsClient client = createClient(hostNode);
        return client.createLocalPod(pod);
    }

    public String deleteRemotePod(Pod pod) {
        List<HostNode> hosts = new ArrayList<>(hostNodeModel.getMap().values());
        for (HostNode hostNode : hosts) {
            try {
                return tryDeletePod(hostNode, pod);
            } catch (Exception e) {
                LOG.warn("Failed to delete pod on host " + hostNode.getWebUrl() + ". Might not be on that pod ;). " + e, e);
            }
        }
        return null;
    }

    protected String tryDeletePod(HostNode hostNode, Pod pod) throws Exception {
        String id = getName(pod);
        LOG.info("Attempting to delete pod: " + id + " on host: " + hostNode.getWebUrl());
        KubernetesExtensionsClient client = createClient(hostNode);
        return client.deleteLocalPod(id, KubernetesHelper.getNamespace(pod));
    }


    private KubernetesExtensionsClient createClient(HostNode hostNode) {
        String webUrl = hostNode.getWebUrl();
        if (Strings.isNullOrBlank(webUrl)) {
            throw new IllegalArgumentException("No WebUrl so could not create client for host: " + hostNode);
        }
        return new KubernetesExtensionsClient(webUrl);
    }


    // Implementation methods
    //-------------------------------------------------------------------------
    protected String zkPathForPod(String id) {
        return KUBERNETES_MODEL + "/" + NodeHelper.KIND_POD + "-" + id;
    }

    protected String zkPathForService(String id) {
        return KUBERNETES_MODEL + "/" + NodeHelper.KIND_SERVICE + "-" + id;
    }

    protected String zkPathForReplicationController(String id, String namespace) {
        if (Strings.isNotBlank(namespace)) {
            namespace = "default";
        }
        return KUBERNETES_MODEL + "/" + NodeHelper.KIND_REPLICATION_CONTROLLER + "-" + namespace + "-" + id;
    }


    protected void writeEntity(String path, Object entity) {
        try {
            String json = KubernetesHelper.toJson(entity);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing to path: " + path + " json: " + json);
            }
            if (curator.checkExists().forPath(path) == null) {
                curator.create().creatingParentsIfNeeded().forPath(path, json.getBytes());
            } else {
                curator.setData().forPath(path, json.getBytes());
            }
            updateLocalModel(entity, false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update object at path: " + path + ". " + e, e);
        }
    }

    protected void deleteEntity(String path) {
        try {
            Stat stat = curator.checkExists().forPath(path);
            if (stat != null) {
                curator.delete().deletingChildrenIfNeeded().forPath(path);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object at path: " + path + ". " + e, e);
        }
    }

    protected void treeCacheEvent(TreeCacheEvent event) {
        ChildData childData = event.getData();
        if (childData == null) {
            return;
        }
        String path = childData.getPath();
        TreeCacheEvent.Type type = event.getType();
        byte[] data = childData.getData();
        if (data == null || data.length == 0 || path == null) {
            return;
        }
        if (path.startsWith(KUBERNETES_MODEL)) {
            path = path.substring(KUBERNETES_MODEL.length());
        }
        boolean remove = false;
        switch (type) {
        case NODE_ADDED:
        case NODE_UPDATED:
            break;
        case NODE_REMOVED:
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
        if (dto instanceof Pod) {
            podChanged((Pod) dto, remove);
        } else if (dto instanceof ReplicationController) {
            replicationControllerChanged((ReplicationController) dto, remove);
        } else if (dto instanceof Service) {
            serviceChanged((Service) dto, remove);
        } else {
            LOG.warn("Unrecognised DTO: " + dto);
        }
    }

    protected void podChanged(Pod entity, boolean remove) {
        if (remove) {
            String id = getName(entity);
            if (Strings.isNotBlank(id)) {
                memoryModel.deletePod(id, KubernetesHelper.getNamespace(entity));
                podListeners.entityDeleted(id, entity);

            }
        } else {
            String id = memoryModel.getOrCreateId(getName(entity), NodeHelper.KIND_POD);
            Pod old = memoryModel.getPod(id);
            // lets only replace the Pod if it really has changed to avoid overwriting
            // pods which are being installed
            if (NodeHelper.podHasChanged(entity, old)) {
                memoryModel.updatePod(id, entity);
                podListeners.entityChanged(id, entity);
            }
        }
    }

    protected void replicationControllerChanged(ReplicationController entity, boolean remove) {
        if (remove) {
            String id = getName(entity);
            if (Strings.isNotBlank(id)) {
                memoryModel.deleteReplicationController(id, KubernetesHelper.getNamespace(entity));
                replicationControllerListeners.entityDeleted(id, entity);
            }
        } else {
            String id = memoryModel.getOrCreateId(getName(entity), NodeHelper.KIND_REPLICATION_CONTROLLER);
            memoryModel.updateReplicationController(id, entity);
            replicationControllerListeners.entityChanged(id, entity);
        }
    }

    protected void serviceChanged(Service entity, boolean remove) {
        if (remove) {
            String id = getName(entity);
            if (Strings.isNotBlank(id)) {
                memoryModel.deleteService(id, KubernetesHelper.getNamespace(entity));
                serviceListeners.entityDeleted(id, entity);
            }
        } else {
            String id = memoryModel.getOrCreateId(getName(entity), NodeHelper.KIND_SERVICE);
            memoryModel.updateService(id, entity);
            serviceListeners.entityChanged(id, entity);
        }
    }
}
