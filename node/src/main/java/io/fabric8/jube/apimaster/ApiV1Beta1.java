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
package io.fabric8.jube.apimaster;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import io.fabric8.jube.model.HostNode;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.Minion;
import io.fabric8.kubernetes.api.model.MinionList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;

// TODO: delete me when v1beta1 is no longer supported

/**
 */
@Singleton
@Path("v1beta1")
@Produces("application/json")
@Consumes("application/json")
public class ApiV1Beta1 implements KubernetesExtensions {
    private final ApiMasterService delegate;

    @Inject
    public ApiV1Beta1(ApiMasterService delegate) {
        this.delegate = delegate;
    }

    @GET
    @Path("hostNodes/{id}")
    @Produces("application/json")
    public HostNode getHostNode(@NotNull String id) {
        return delegate.getHostNode(id);
    }

    public Map<String, String> createKubernetesServiceLabels() {
        return delegate.createKubernetesServiceLabels();
    }

    public Map<String, String> createFabric8ConsoleServiceLabels() {
        return delegate.createFabric8ConsoleServiceLabels();
    }

    public void setNamespace(String namespace) {
        delegate.setNamespace(namespace);
    }

    public String getNamespace() {
        return delegate.getNamespace();
    }

    public String updateLocalPod(@NotNull String podId, Pod pod) throws Exception {
        return delegate.updateLocalPod(podId, pod);
    }

    public Service createService(String hostName, String port) {
        return delegate.createService(hostName, port);
    }

    public List<Pod> podList() {
        return delegate.podList();
    }

    @GET
    @Path("hostNodes")
    @Produces("application/json")
    public Map<String, HostNode> getHostNodes() {
        return delegate.getHostNodes();
    }

    public void ensureModelHasKubernetesServices(String hostName, String port) {
        delegate.ensureModelHasKubernetesServices(hostName, port);
    }

    @POST
    @Path("local/pods")
    @Consumes("application/json")
    @Override
    public String createLocalPod(Pod entity) throws Exception {
        return delegate.createLocalPod(entity);
    }

    public String createPod(Pod entity) throws Exception {
        return delegate.createPod(entity);
    }

    @Override
    public String createPod(Pod pod, String namespace) throws Exception {
        return delegate.createPod(pod, namespace);
    }

    public String createReplicationController(ReplicationController entity) throws Exception {
        return delegate.createReplicationController(entity);
    }

    @Override
    public String createReplicationController(ReplicationController replicationController, String namespace) throws Exception {
        return delegate.createReplicationController(replicationController, namespace);
    }

    @Override
    public String updateReplicationController(@NotNull String id, ReplicationController replicationController, String namespace) throws Exception {
        return delegate.updateReplicationController(id, replicationController, namespace);
    }

    public String createService(Service entity) throws Exception {
        return delegate.createService(entity);
    }

    @Override
    public String createService(Service service, String namespace) throws Exception {
        return delegate.createService(service, namespace);
    }

    @DELETE
    @Path("local/pods/{id}")
    @Consumes("text/plain")
    @Override
    public String deleteLocalPod(@NotNull String id, String namespace) throws Exception {
        return delegate.deleteLocalPod(id, namespace);
    }

    public String deletePod(@NotNull String podId) throws Exception {
        return delegate.deletePod(podId);
    }

    public String deletePod(@NotNull String podId, String namespace) throws Exception {
        return delegate.deletePod(podId, namespace);
    }

    public String deleteReplicationController(@NotNull String controllerId) throws Exception {
        return delegate.deleteReplicationController(controllerId);
    }

    @Override
    public String deleteReplicationController(@NotNull String controllerId, String namespace) throws Exception {
        return delegate.deleteReplicationController(controllerId, namespace);
    }

    @Override
    public EndpointsList getEndpoints(String s) {
        return delegate.getEndpoints(s);
    }

    public String deleteService(@NotNull String serviceId) throws Exception {
        return delegate.deleteService(serviceId);
    }

    @Override
    public String deleteService(@NotNull String serviceId, String namespace) throws Exception {
        return delegate.deleteService(serviceId, namespace);
    }

    @Override
    public Endpoints endpointsForService(@NotNull String serviceId, String namespace) {
        return delegate.endpointsForService(serviceId, namespace);
    }

    public EndpointsList getEndpoints() {
        return delegate.getEndpoints();
    }

    @GET
    @Path("local/pods")
    @Consumes("application/json")
    @Override
    public PodList getLocalPods() {
        return delegate.getLocalPods();
    }

    @Override
    public MinionList getMinions() {
        return delegate.getMinions();
    }

    public Pod getPod(@NotNull String podId) {
        return delegate.getPod(podId);
    }

    @Override
    public Pod getPod(@NotNull String podId, String namespace) {
        return delegate.getPod(podId, namespace);
    }

    public PodList getPods() {
        return delegate.getPods();
    }

    @Override
    public PodList getPods(String namespace) {
        return delegate.getPods(namespace);
    }

    public ReplicationController getReplicationController(@NotNull String controllerId) {
        return delegate.getReplicationController(controllerId);
    }

    @Override
    public ReplicationController getReplicationController(@NotNull String replicationControllerId, String namespace) {
        return delegate.getReplicationController(replicationControllerId, namespace);
    }

    public ReplicationControllerList getReplicationControllers() {
        return delegate.getReplicationControllers();
    }

    @Override
    public ReplicationControllerList getReplicationControllers(String namespace) {
        return delegate.getReplicationControllers(namespace);
    }

    public Service getService(@NotNull String serviceId) {
        return delegate.getService(serviceId);
    }

    @Override
    public Service getService(@NotNull String serviceId, String namespace) {
        return delegate.getService(serviceId, namespace);
    }

    public ServiceList getServices() {
        return delegate.getServices();
    }

    @Override
    public ServiceList getServices(String namespace) {
        return delegate.getServices(namespace);
    }

    @Override
    public Minion minion(@NotNull String name) {
        return delegate.minion(name);
    }

    public String updatePod(@NotNull String podId, Pod pod) throws Exception {
        return delegate.updatePod(podId, pod);
    }

    @Override
    public String updatePod(@NotNull String podId, Pod pod, String namespace) throws Exception {
        return delegate.updatePod(podId, pod, namespace);
    }

    public String updateReplicationController(@NotNull String controllerId, ReplicationController replicationController) throws Exception {
        return delegate.updateReplicationController(controllerId, replicationController);
    }

    public String updateService(@NotNull String id, Service entity) throws Exception {
        return delegate.updateService(id, entity);
    }

    @Override
    public String updateService(@NotNull String serviceId, Service service, String namespace) throws Exception {
        return delegate.updateService(serviceId, service, namespace);
    }
}
