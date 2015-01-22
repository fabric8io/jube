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
package io.fabric8.jube;

import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.fabric8.jube.local.PodCurrentContainer;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.utils.Filter;

/**
 */
public interface KubernetesModel {
    ImmutableMap<String, Pod> getPodMap();

    PodList getPods();

    ImmutableList<Pod> getPods(Map<String, String> replicaSelector);

    ImmutableList<Pod> getPods(Filter<Pod> podFilter);

    Pod getPod(String id);

    void updatePod(String id, Pod pod);

    String getOrCreateId(String id, String kind);

    boolean updatePodIfNotExist(String id, Pod pod);

    Pod deletePod(String podId, String namespace);

    ImmutableMap<String, PodCurrentContainer> getPodRunningContainers(KubernetesModel model);

    ReplicationController getReplicationController(String id);

    ReplicationControllerList getReplicationControllers();

    ImmutableMap<String, ReplicationController> getReplicationControllerMap();

    void updateReplicationController(String id, ReplicationController replicationController);

    void deleteReplicationController(String controllerId, String namespace);

    ServiceList getServices();

    Service getService(String id);

    ImmutableMap<String, Service> getServiceMap();

    void updateService(String id, Service entity);

    void deleteService(String serviceId, String namespace);

    String createID(String kind);
}
