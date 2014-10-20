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

import io.fabric8.kubernetes.api.Kubernetes;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.PodListSchema;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerListSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.ServiceListSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Path;
import java.util.Map;

/**
 * Implements the local node controller
 */
@Path("api/v1beta1")
public class LocalNodeController implements Kubernetes {
    @Override
    public PodListSchema getPods() {
        PodListSchema answer = new PodListSchema();
        // TODO
        return answer;
    }

    @Override
    public PodSchema getPod(@NotNull String podId) {
        Map<String, PodSchema> map = KubernetesHelper.getPodMap(this);
        return map.get(podId);
    }

    @Override
    public String createPod(PodSchema entity) throws Exception {
        // TODO
        return null;
    }

    @Override
    public String updatePod(@NotNull String podId, PodSchema entity) throws Exception {
        // TODO
        return null;
    }

    @Override
    public String deletePod(@NotNull String podId) throws Exception {
        // TODO
        return null;
    }

    @Override
    public ServiceListSchema getServices() {
        ServiceListSchema answer = new ServiceListSchema();
        // TODO
        return answer;
    }

    @Override
    public ServiceSchema getService(@NotNull String serviceId) {
        Map<String, ServiceSchema> map = KubernetesHelper.getServiceMap(this);
        return map.get(serviceId);
    }

    @Override
    public String createService(ServiceSchema entity) throws Exception {
        // TODO
        return null;
    }

    @Override
    public String updateService(@NotNull String serviceId, ServiceSchema entity) throws Exception {
        // TODO
        return null;
    }

    @Override
    public String deleteService(@NotNull String serviceId) throws Exception {
        // TODO
        return null;
    }

    @Override
    public ReplicationControllerListSchema getReplicationControllers() {
        ReplicationControllerListSchema answer = new ReplicationControllerListSchema();
        // TODO
        return answer;
    }

    @Override
    public ReplicationControllerSchema getReplicationController(@NotNull String controllerId) {
        Map<String, ReplicationControllerSchema> map = KubernetesHelper.getReplicationControllerMap(this);
        return map.get(controllerId);
    }

    @Override
    public String createReplicationController(ReplicationControllerSchema entity) throws Exception {
        // TODO
        return null;
    }

    @Override
    public String updateReplicationController(@NotNull String controllerId, ReplicationControllerSchema entity) throws Exception {
        // TODO
        return null;
    }

    @Override
    public String deleteReplicationController(@NotNull String controllerId) throws Exception {
        // TODO
        return null;
    }
}
