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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.fabric8.jube.ServiceIDs;
import io.fabric8.jube.Statuses;
import io.fabric8.jube.local.NodeHelper;
import io.fabric8.jube.local.ProcessMonitor;
import io.fabric8.jube.model.HostNode;
import io.fabric8.jube.model.HostNodeModel;
import io.fabric8.jube.process.Installation;
import io.fabric8.jube.process.ProcessManager;
import io.fabric8.jube.proxy.KubeProxy;
import io.fabric8.jube.replicator.Replicator;
import io.fabric8.kubernetes.api.IntOrString;
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
import io.fabric8.utils.Objects;
import io.hawt.util.Strings;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fabric8.jube.local.NodeHelper.getOrCreateCurrentState;

/**
 * Implements the local node controller
 */
@Singleton
@Path("v1beta1")
@Produces("application/json")
@Consumes("application/json")
public class ApiMasterService implements KubernetesExtensions {

    public static final String DEFAULT_HOSTNAME = "localhost";
    public static final String DEFAULT_HTTP_PORT = "8585";
    public static final String DEFAULT_NAMESPACE = "default";

    public static String hostName = DEFAULT_HOSTNAME;
    public static String port = DEFAULT_HTTP_PORT;

    private static final transient Logger LOG = LoggerFactory.getLogger(ApiMasterService.class);

    private final ProcessManager processManager;
    private final ApiMasterKubernetesModel model;
    private final Replicator replicator;
    private final ProcessMonitor processMonitor;
    private final KubeProxy kubeProxy;
    private final HostNodeModel hostNodeModel;
    private final ExecutorService localCreateThreadPool = Executors.newFixedThreadPool(10);

    @Inject
    public ApiMasterService(ProcessManager processManager, ApiMasterKubernetesModel model, Replicator replicator, ProcessMonitor processMonitor, KubeProxy kubeProxy, HostNodeModel hostNodeModel,
                            @ConfigProperty(name = "JUBE_HOSTNAME", defaultValue = DEFAULT_HOSTNAME)
                            String hostName,
                            @ConfigProperty(name = "HTTP_PORT", defaultValue = DEFAULT_HTTP_PORT)
                            String port) {
        this.processManager = processManager;
        this.model = model;
        this.replicator = replicator;
        this.processMonitor = processMonitor;
        this.kubeProxy = kubeProxy;
        this.hostNodeModel = hostNodeModel;

        ApiMasterService.hostName = hostName;
        ApiMasterService.port = port;

        HostNode node = new HostNode();
        node.setHostName(hostName);
        node.setWebUrl("http://" + hostName + ":" + port + "/");
        node.setId(UUID.randomUUID().toString());
        hostNodeModel.write(node);

        ensureModelHasKubernetesServices(hostName, port);
    }

    public static String getHostName() {
        return hostName;
    }

    public static String getPort() {
        return port;
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
        return model.remoteCreatePod(entity);
    }


    @Override
    public String updatePod(final @NotNull String podId, final PodSchema pod) throws Exception {
        // TODO needs implementing remotely!

        return NodeHelper.excludeFromProcessMonitor(processMonitor, pod, new Callable<String>() {
            @Override
            public String call() throws Exception {
                System.out.println("Updating pod " + pod);
                DesiredState desiredState = pod.getDesiredState();
                Objects.notNull(desiredState, "desiredState");

                CurrentState currentState = getOrCreateCurrentState(pod);
                List<ManifestContainer> containers = KubernetesHelper.getContainers(pod);
                model.updatePod(podId, pod);

                return NodeHelper.createMissingContainers(processManager, model, pod, currentState, containers);
            }
        });
    }

    @Override
    public String deletePod(@NotNull String podId) throws Exception {
        model.deletePod(podId);
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
        // lets ensure there's a default namespace set
        String namespace = replicationController.getNamespace();
        if (Strings.isBlank(namespace)) {
            replicationController.setNamespace(DEFAULT_NAMESPACE);
        }
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
        // lets set the IP
        entity.setPortalIP(getHostName());

        // lets ensure there's a default namespace set
        String namespace = entity.getNamespace();
        if (Strings.isBlank(namespace)) {
            entity.setNamespace(DEFAULT_NAMESPACE);
        }
        model.updateService(id, entity);
        return null;
    }

    @Override
    public String deleteService(@NotNull String serviceId) throws Exception {
        model.deleteService(serviceId);
        return null;
    }

    // Host nodes
    //-------------------------------------------------------------------------

    @GET
    @Path("hostNodes")
    @Produces("application/json")
    public Map<String, HostNode> getHostNodes() {
        return hostNodeModel.getMap();
    }

    @GET
    @Path("hostNodes/{id}")
    @Produces("application/json")
    public HostNode getHostNode(@PathParam("id") @NotNull String id) {
        return hostNodeModel.getEntity(id);
    }


    // Local operations
    //-------------------------------------------------------------------------

    @POST
    @Path("local/pods")
    @Consumes("application/json")
    @Override
    public String createLocalPod(PodSchema entity) throws Exception {
        String id = model.getOrCreateId(entity.getId(), NodeHelper.KIND_REPLICATION_CONTROLLER);
        entity.setId(id);
        return updateLocalPod(id, entity);
    }

    public String updateLocalPod(@NotNull final String podId, final PodSchema pod) throws Exception {
        System.out.println("Updating pod " + pod);
        DesiredState desiredState = pod.getDesiredState();
        Objects.notNull(desiredState, "desiredState");

        // lets ensure there's a default namespace set
        String namespace = pod.getNamespace();
        if (Strings.isBlank(namespace)) {
            pod.setNamespace(DEFAULT_NAMESPACE);
        }

        final CurrentState currentState = getOrCreateCurrentState(pod);
        final List<ManifestContainer> containers = KubernetesHelper.getContainers(pod);

        NodeHelper.setPodStatus(pod, Statuses.WAITING);
        NodeHelper.setContainerRunningState(pod, podId, false);

        model.updatePod(podId, pod);
        localCreateThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                Runnable task = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            NodeHelper.createMissingContainers(processManager, model, pod, currentState, containers);
                        } catch (Exception e) {
                            LOG.error("Failed to create container " + podId + ". " + e, e);
                        }

                    }
                };
                NodeHelper.excludeFromProcessMonitor(processMonitor, pod, task);
            }
        });
        return pod.getId();
    }


    @DELETE
    @Path("local/pods/{id}")
    @Consumes("text/plain")
    @Override
    public String deleteLocalPod(@PathParam("id") @NotNull String id) throws Exception {
        NodeHelper.deletePod(processManager, model, id);
        return null;
    }

    @GET
    @Path("local/pods")
    @Consumes("application/json")
    @Override
    public PodListSchema getLocalPods() {
        ImmutableMap<String, Installation> installMap = processManager.listInstallationMap();
        ImmutableSet<String> keys = installMap.keySet();
        List<PodSchema> pods = new ArrayList<>();
        for (String key : keys) {
            PodSchema pod = model.getPod(key);
            if (pod != null) {
                pods.add(pod);
            }
        }
        PodListSchema answer = new PodListSchema();
        answer.setItems(pods);
        return answer;

    }


    /**
     * Lets ensure that the "kubernetes" and "kubernetes-ro" services are defined so that folks can access the core
     * REST API via kubernetes services
     */
    protected void ensureModelHasKubernetesServices(String hostName, String port) {
        ImmutableMap<String, ServiceSchema> serviceMap = model.getServiceMap();
        ServiceSchema service = null;
        try {
            service = serviceMap.get(ServiceIDs.KUBERNETES_SERVICE_ID);
            if (service == null) {
                service = createService(hostName, port);
                service.setId(ServiceIDs.KUBERNETES_SERVICE_ID);
                service.setSelector(createKubernetesServiceLabels());
                createService(service);
            }
            service = serviceMap.get(ServiceIDs.KUBERNETES_RO_SERVICE_ID);
            if (service == null) {
                service = createService(hostName, port);
                service.setId(ServiceIDs.KUBERNETES_RO_SERVICE_ID);
                service.setSelector(createKubernetesServiceLabels());
                createService(service);
            }
        } catch (Exception e) {
            LOG.error("Failed to create service " + service + ". " + e, e);
        }
    }

    protected Map<String,String> createKubernetesServiceLabels() {
        Map<String, String> answer = new HashMap<>();
        answer.put("component", "apiserver");
        answer.put("provider", "kubernetes");
        return answer;
    }

    protected ServiceSchema createService(String hostName, String port) {
        ServiceSchema service = new ServiceSchema();
        service.setKind("Service");
        try {
            Integer portNumber = Integer.parseInt(port);
            if (portNumber != null) {
                service.setPort(portNumber);
                IntOrString containerPort = new IntOrString();
                containerPort.setIntValue(portNumber);
                service.setContainerPort(containerPort);
            }
        } catch (NumberFormatException e) {
            LOG.warn("Failed to parse port text: " + port + ". " + e, e);
        }
        service.setPortalIP(hostName);
        return service;
    }

}
