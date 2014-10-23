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
package org.jboss.jube;

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
import org.jboss.jube.local.PodCurrentContainer;

import java.util.Map;

/**
 */
public interface KubernetesModel {
    ImmutableMap<String, PodSchema> getPodMap();

    PodListSchema getPods();

    ImmutableList<PodSchema> getPods(Map<String, String> replicaSelector);

    ImmutableList<PodSchema> getPods(Filter<PodSchema> podFilter);

    PodSchema getPod(String id);

    void updatePod(String id, PodSchema pod);

    boolean updatePodIfNotExist(String id, PodSchema pod);

    PodSchema deletePod(String podId);

    ImmutableMap<String, PodCurrentContainer> getPodRunningContainers();

    PodCurrentContainer createPodCurrentContainer(String podId, PodSchema podSchema, String containerId, PodCurrentContainerInfo currentContainer);

    ReplicationControllerSchema getReplicationController(String id);

    ReplicationControllerListSchema getReplicationControllers();

    ImmutableMap<String, ReplicationControllerSchema> getReplicationControllerMap();

    void updateReplicationController(String id, ReplicationControllerSchema replicationController);

    void deleteReplicationController(String controllerId);

    ServiceListSchema getServices();

    ServiceSchema getService(String id);

    ImmutableMap<String, ServiceSchema> getServiceMap();

    void updateService(String id, ServiceSchema entity);

    void deleteService(String serviceId);

    String createID(String kind);
}
