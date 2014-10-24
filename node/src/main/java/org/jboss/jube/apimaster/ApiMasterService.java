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

import io.fabric8.common.util.Objects;
import io.fabric8.kubernetes.api.Kubernetes;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.CurrentState;
import io.fabric8.kubernetes.api.model.DesiredState;
import io.fabric8.kubernetes.api.model.ManifestContainer;
import io.fabric8.kubernetes.api.model.PodListSchema;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerListSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.ServiceListSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.hawt.util.Strings;
import org.jboss.jube.KubernetesModel;
import org.jboss.jube.apimaster.ApiMasterKubernetesModel;
import org.jboss.jube.local.NodeHelper;
import org.jboss.jube.local.ProcessMonitor;
import org.jboss.jube.process.ProcessManager;
import org.jboss.jube.proxy.KubeProxy;
import org.jboss.jube.replicator.Replicator;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.Map;

import static org.jboss.jube.local.NodeHelper.getOrCreateCurrentState;

/**
 * Implements the local node controller
 */
@Singleton
@Path("api/v1beta1")
@Produces("application/json")
@Consumes("application/json")
public class ApiMasterService implements Kubernetes {
    private final ProcessManager processManager;
    private final KubernetesModel model;
    private final Replicator replicator;
    private final ProcessMonitor processMonitor;
    private final KubeProxy kubeProxy;

    @Inject
    public ApiMasterService(ProcessManager processManager, ApiMasterKubernetesModel model, Replicator replicator, ProcessMonitor processMonitor, KubeProxy kubeProxy) {
        this.processManager = processManager;
        this.model = model;
        this.replicator = replicator;
        this.processMonitor = processMonitor;
        this.kubeProxy = kubeProxy;
    }

    // Pods
    //-------------------------------------------------------------------------


    @Override
    public PodListSchema getPods() {
        return model.getPods();
    }

    @Override
    public PodSchema getPod(@NotNull String podId) {
        Map<String, PodSchema> map = model.getPodMap();
        return map.get(podId);
    }

    @Override
    public String createPod(PodSchema entity) throws Exception {
        String id = model.getOrCreateId(entity.getId(), NodeHelper.KIND_REPLICATION_CONTROLLER);
        entity.setId(id);
        return updatePod(id, entity);
    }


    @Override
    public String updatePod(@NotNull String podId, PodSchema pod) throws Exception {
        System.out.println("Updating pod " + pod);
        DesiredState desiredState = pod.getDesiredState();
        Objects.notNull(desiredState, "desiredState");

        CurrentState currentState = getOrCreateCurrentState(pod);
        List<ManifestContainer> containers = KubernetesHelper.getContainers(pod);
        model.updatePod(podId, pod);
        return NodeHelper.createMissingContainers(processManager, pod, currentState, containers);
    }

    @Override
    public String deletePod(@NotNull String podId) throws Exception {
        NodeHelper.deletePod(processManager, model, podId);
        return null;
    }


    // Replication Controllers
    //-------------------------------------------------------------------------


    @Override
    public ReplicationControllerListSchema getReplicationControllers() {
        return model.getReplicationControllers();
    }

    @Override
    public ReplicationControllerSchema getReplicationController(@NotNull String controllerId) {
        Map<String, ReplicationControllerSchema> map = KubernetesHelper.getReplicationControllerMap(this);
        return map.get(controllerId);
    }

    @Override
    public String createReplicationController(ReplicationControllerSchema entity) throws Exception {
        String id = model.getOrCreateId(entity.getId(), NodeHelper.KIND_REPLICATION_CONTROLLER);
        entity.setId(id);
        return updateReplicationController(id, entity);
    }

    @Override
    public String updateReplicationController(@NotNull String controllerId, ReplicationControllerSchema replicationController) throws Exception {
        model.updateReplicationController(controllerId, replicationController);
        return null;
    }

    @Override
    public String deleteReplicationController(@NotNull String controllerId) throws Exception {
        model.deleteReplicationController(controllerId);
        return null;
    }

    // Services
    //-------------------------------------------------------------------------

    @Override
    public ServiceListSchema getServices() {
        return model.getServices();
    }

    @Override
    public ServiceSchema getService(@NotNull String serviceId) {
        Map<String, ServiceSchema> map = model.getServiceMap();
        return map.get(serviceId);
    }

    @Override
    public String createService(ServiceSchema entity) throws Exception {
        String id = model.getOrCreateId(entity.getId(), NodeHelper.KIND_SERVICE);
        entity.setId(id);
        return updateService(id, entity);
    }

    @Override
    public String updateService(@NotNull String id, ServiceSchema entity) throws Exception {
        model.updateService(id, entity);
        return null;
    }

    @Override
    public String deleteService(@NotNull String serviceId) throws Exception {
        model.deleteService(serviceId);
        return null;
    }

}
