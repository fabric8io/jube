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
import java.util.Collection;
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
import javax.ws.rs.QueryParam;

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
import io.fabric8.kubernetes.api.Kubernetes;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeSpec;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.util.IntOrString;
import io.fabric8.utils.Objects;
import io.hawt.util.Strings;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.kubernetes.api.KubernetesHelper.getOrCreateMetadata;
import static io.fabric8.kubernetes.api.KubernetesHelper.getOrCreateSpec;
import static io.fabric8.kubernetes.api.KubernetesHelper.setName;
import static io.fabric8.kubernetes.api.KubernetesHelper.setSelector;
import static io.fabric8.utils.Lists.notNullList;

/**
 * Implements the local node controller
 */
@Singleton
@Path("v1beta2")
@Produces("application/json")
@Consumes("application/json")
public class ApiMasterService implements KubernetesExtensions {

    public static final String DEFAULT_HOSTNAME = "localhost";
    public static final String DEFAULT_HTTP_PORT = "8585";
    public static final String DEFAULT_NAMESPACE = "default";
    private static final String HEADLESS_PORTAL_IP = "None";

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
    private String namespace = "default";

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

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    // Pods
    //-------------------------------------------------------------------------


    public PodList getPods() {
        return model.getPods();
    }

    public Pod getPod(@NotNull String podId) {
        Map<String, Pod> map = model.getPodMap();
        return map.get(podId);
    }

    @Override
    public Pod getPod(@NotNull String podId, String namespace) {
        return model.getPod(podId, namespace);
    }

    @Override
    public PodList getPods(String namespace) {
        return model.getPods(namespace);
    }

    public String createPod(Pod entity) throws Exception {
        return model.remoteCreatePod(entity);
    }

    @Override
    public String createPod(Pod pod, String namespace) throws Exception {
        getOrCreateMetadata(pod).setNamespace(namespace);
        return createPod(pod);
    }

    public String updatePod(final @NotNull String podId, final Pod pod) throws Exception {
        // TODO needs implementing remotely!

        return NodeHelper.excludeFromProcessMonitor(processMonitor, pod, new Callable<String>() {
            @Override
            public String call() throws Exception {
                System.out.println("Updating pod " + pod);
                PodSpec desiredState = pod.getSpec();
                Objects.notNull(desiredState, "desiredState");

                PodStatus currentState = NodeHelper.getOrCreatetStatus(pod);
                List<Container> containers = KubernetesHelper.getContainers(pod);
                model.updatePod(podId, pod);

                return NodeHelper.createMissingContainers(processManager, model, pod, currentState, containers);
            }
        });
    }

    public String deletePod(@NotNull String podId) throws Exception {
        model.deletePod(podId, namespace);
        return null;
    }

    @Override
    public String deletePod(@NotNull String podId, String namespace) throws Exception {
        model.deletePod(podId, namespace);
        return null;
    }

    @Override
    public String updatePod(@NotNull String podId, Pod pod, String namespace) throws Exception {
        getOrCreateMetadata(pod).setNamespace(namespace);
        return updatePod(podId, pod);
    }

    @Override
    public ServiceList getServices(String namespace) {
        return model.getServices(namespace);
    }

    @Override
    public Service getService(@NotNull String serviceId, String namespace) {
        return model.getService(serviceId, namespace);
    }

    @Override
    public String updateService(@NotNull String serviceId, Service service, String namespace) throws Exception {
        getOrCreateMetadata(service).setNamespace(namespace);
        return updateService(serviceId, service);
    }

    // Replication Controllers
    //-------------------------------------------------------------------------


    public ReplicationControllerList getReplicationControllers() {
        return model.getReplicationControllers();
    }

    public ReplicationController getReplicationController(@NotNull String controllerId) {
        Map<String, ReplicationController> map = KubernetesHelper.getReplicationControllerMap(this);
        return map.get(controllerId);
    }

    @Override
    public ReplicationControllerList getReplicationControllers(String namespace) {
        return model.getReplicationControllers(namespace);
    }

    @Override
    public ReplicationController getReplicationController(@NotNull String replicationControllerId, String namespace) {
        return model.getReplicationController(replicationControllerId, namespace);
    }

    public String createReplicationController(ReplicationController entity) throws Exception {
        String id = model.getOrCreateId(getName(entity), NodeHelper.KIND_REPLICATION_CONTROLLER);
        setName(entity, id);
        return updateReplicationController(id, entity);
    }

    @Override
    public String createReplicationController(ReplicationController replicationController, String namespace) throws Exception {
        getOrCreateMetadata(replicationController).setNamespace(namespace);
        return createReplicationController(replicationController);
    }

    @Override
    public String updateReplicationController(@PathParam("controllerId") @NotNull String s, ReplicationController replicationController, @QueryParam("namespace") String s1) throws Exception {
        getOrCreateMetadata(replicationController).setNamespace(namespace);
        return updateReplicationController(s, replicationController);
    }


    public String updateReplicationController(@NotNull String controllerId, ReplicationController replicationController) throws Exception {
        // lets ensure there's a default namespace set
        String namespace = KubernetesHelper.getNamespace(replicationController);
        if (Strings.isBlank(namespace)) {
            getOrCreateMetadata(replicationController).setNamespace(DEFAULT_NAMESPACE);
        }
        model.updateReplicationController(controllerId, replicationController);
        return null;
    }

    public String deleteReplicationController(@NotNull String controllerId) throws Exception {
        model.deleteReplicationController(controllerId, namespace);
        return null;
    }

    @Override
    public String deleteReplicationController(@NotNull String controllerId, String namespace) throws Exception {
        model.deleteReplicationController(controllerId, namespace);
        return null;
    }

    @Override
    public EndpointsList getEndpoints(@QueryParam("namespace") String namespace) {
        EndpointsList answer = new EndpointsList();
        List<Endpoints> list = new ArrayList<>();
        answer.setItems(list);

        ServiceList services = getServices(namespace);
        if (services != null) {
            List<Pod> pods = getPods(namespace).getItems();
            List<Service> items = notNullList(services.getItems());
            for (Service service : items) {
                Endpoints endpoints = createEndpointsForService(service, pods);
                if (endpoints != null) {
                    list.add(endpoints);
                }
            }
        }
        return answer;
    }


    // Services
    //-------------------------------------------------------------------------

    public ServiceList getServices() {
        return model.getServices();
    }

    public Service getService(@NotNull String serviceId) {
        Map<String, Service> map = model.getServiceMap();
        return map.get(serviceId);
    }

    public String createService(Service entity) throws Exception {
        String id = model.getOrCreateId(getName(entity), NodeHelper.KIND_SERVICE);
        setName(entity, id);
        return updateService(id, entity);
    }

    @Override
    public String createService(Service service, String namespace) throws Exception {
        getOrCreateMetadata(service).setNamespace(namespace);
        return createService(service);
    }


    public String updateService(@NotNull String id, Service entity) throws Exception {
        // lets set the IP
        ServiceSpec spec = entity.getSpec();
        if (spec == null) {
            spec = new ServiceSpec();
            entity.setSpec(spec);
        }
        if (!HEADLESS_PORTAL_IP.equals(spec.getPortalIP())) {
            spec.setPortalIP(getHostName());
        }

        // lets ensure there's a default namespace set
        String namespace = KubernetesHelper.getNamespace(entity);
        if (Strings.isBlank(namespace)) {
            getOrCreateMetadata(entity).setNamespace(DEFAULT_NAMESPACE);
        }
        model.updateService(id, entity);
        return null;
    }

    public String deleteService(@NotNull String serviceId) throws Exception {
        model.deleteService(serviceId, namespace);
        return null;
    }

    @Override
    public String deleteService(@NotNull String serviceId, String namespace) throws Exception {
        model.deleteService(serviceId, namespace);
        return null;
    }

    @Override
    public Endpoints endpointsForService(@NotNull String serviceId, String namespace) {
        List<Pod> pods = podList();
        Service service = getService(serviceId);
        return createEndpointsForService(service, pods);
    }

    protected List<Pod> podList() {
        PodList podList = getPods();
        return notNullList(podList.getItems());
    }

    protected static Endpoints createEndpointsForService(Service service, List<Pod> pods) {
        if (service == null) {
            return null;
        }
        ServiceSpec serviceSpec = service.getSpec();
        if (serviceSpec == null) {
            return null;
        }
        Endpoints answer = new Endpoints();
        answer.setName(getName(service));
        List<String> urls = new ArrayList<>();
        answer.setEndpoints(urls);
        IntOrString containerPort = serviceSpec.getContainerPort();
        if (containerPort != null) {
            String strVal = containerPort.getStrVal();
            if (strVal == null) {
                Integer intVal = containerPort.getIntVal();
                if (intVal != null) {
                    strVal = intVal.toString();
                }
            }
            if (strVal != null) {
                String containerPortText = ":" + strVal;
                List<Pod> podsForService = KubernetesHelper.getPodsForService(service, pods);
                for (Pod pod : podsForService) {
                    PodStatus currentState = pod.getStatus();
                    if (currentState != null) {
                        String podIP = currentState.getPodIP();
                        if (podIP != null) {
                            String url = podIP + containerPortText;
                        }
                    }
                }
            }
        }
        return answer;
    }

    public EndpointsList getEndpoints() {
        EndpointsList answer = new EndpointsList();
        List<Endpoints> list = new ArrayList<>();
        answer.setItems(list);

        ServiceList services = getServices();
        if (services != null) {
            List<Pod> pods = podList();
            List<Service> items = notNullList(services.getItems());
            for (Service service : items) {
                Endpoints endpoints = createEndpointsForService(service, pods);
                if (endpoints != null) {
                    list.add(endpoints);
                }
            }
        }
        return answer;
    }

    @Override
    public NodeList getNodes() {
        // TODO we should replace HostNode with Node...
        NodeList answer = new NodeList();
        List<Node> items = new ArrayList<>();
        answer.setItems(items);
        Collection<HostNode> values = getHostNodes().values();
        for (HostNode value : values) {
            Node minion = new Node();
            NodeSpec nodeSpec = new NodeSpec();
            minion.setSpec(nodeSpec);
            minion.setName(value.getId());
            // TODO no hostName on a minion
            //minion.setHostIP(value.getHostName());
            items.add(minion);
        }
        return answer;
    }

    @Override
    public Node minion(@NotNull String name) {
        NodeList minionList = getNodes();
        List<Node> minions = notNullList(minionList.getItems());
        for (Node minion : minions) {
            if (Objects.equal(getName(minion), name)) {
                return minion;
            }
        }
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
    public String createLocalPod(Pod entity) throws Exception {
        String id = model.getOrCreateId(getName(entity), NodeHelper.KIND_REPLICATION_CONTROLLER);
        setName(entity, id);
        return updateLocalPod(id, entity);
    }

    public String updateLocalPod(@NotNull final String podId, final Pod pod) throws Exception {
        System.out.println("Updating pod " + pod);
        PodSpec desiredState = pod.getSpec();
        Objects.notNull(desiredState, "desiredState");

        // lets ensure there's a default namespace set
        String namespace = KubernetesHelper.getNamespace(pod);
        if (Strings.isBlank(namespace)) {
            getOrCreateMetadata(pod).setNamespace(DEFAULT_NAMESPACE);
        }

        final PodStatus currentState = NodeHelper.getOrCreatetStatus(pod);
        final List<Container> containers = KubernetesHelper.getContainers(pod);

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
        return getName(pod);
    }


    @DELETE
    @Path("local/pods/{id}")
    @Consumes("text/plain")
    @Override
    public String deleteLocalPod(@PathParam("id") @NotNull String id, @QueryParam("namespace") String namespace) throws Exception {
        NodeHelper.deletePod(processManager, model, id, namespace);
        return null;
    }

    @GET
    @Path("local/pods")
    @Consumes("application/json")
    @Override
    public PodList getLocalPods() {
        ImmutableMap<String, Installation> installMap = processManager.listInstallationMap();
        ImmutableSet<String> keys = installMap.keySet();
        List<Pod> pods = new ArrayList<>();
        for (String key : keys) {
            Pod pod = model.getPod(key);
            if (pod != null) {
                pods.add(pod);
            }
        }
        PodList answer = new PodList();
        answer.setItems(pods);
        return answer;

    }


    /**
     * Lets ensure that the "kubernetes" and "kubernetes-ro" services are defined so that folks can access the core
     * REST API via kubernetes services
     */
    protected void ensureModelHasKubernetesServices(String hostName, String port) {
        ImmutableMap<String, Service> serviceMap = model.getServiceMap();
        Service service = null;
        try {
            service = serviceMap.get(ServiceIDs.KUBERNETES_SERVICE_ID);
            if (service == null) {
                service = createService(hostName, port);
                setName(service, ServiceIDs.KUBERNETES_SERVICE_ID);
                setSelector(service, createKubernetesServiceLabels());
                createService(service);
            }
            service = serviceMap.get(ServiceIDs.KUBERNETES_RO_SERVICE_ID);
            if (service == null) {
                service = createService(hostName, port);
                setName(service, ServiceIDs.KUBERNETES_RO_SERVICE_ID);
                setSelector(service, createKubernetesServiceLabels());
                createService(service);
            }
            service = serviceMap.get(ServiceIDs.FABRIC8_CONSOLE_SERVICE_ID);
            if (service == null) {
                service = createService(hostName, port);
                setName(service, ServiceIDs.FABRIC8_CONSOLE_SERVICE_ID);
                setSelector(service, createFabric8ConsoleServiceLabels());
                createService(service);
            }
        } catch (Exception e) {
            LOG.error("Failed to create service " + service + ". " + e, e);
        }
    }

    protected Map<String,String> createFabric8ConsoleServiceLabels() {
        Map<String, String> answer = new HashMap<>();
        answer.put("component", "fabric8Console");
        return answer;
    }

    protected Map<String,String> createKubernetesServiceLabels() {
        Map<String, String> answer = new HashMap<>();
        answer.put("component", "apiserver");
        answer.put("provider", "kubernetes");
        return answer;
    }

    protected Service createService(String hostName, String port) {
        Service service = new Service();
        ServiceSpec spec = getOrCreateSpec(service);
        try {
            Integer portNumber = Integer.parseInt(port);
            if (portNumber != null) {
                List<ServicePort> ports = new ArrayList<>();
                ServicePort servicePort = new ServicePort();
                servicePort.setPort(portNumber);
                IntOrString containerPort = new IntOrString();
                containerPort.setIntVal(portNumber);
                servicePort.setTargetPort(containerPort);

                ports.add(servicePort);
                spec.setPorts(ports);
            }
        } catch (NumberFormatException e) {
            LOG.warn("Failed to parse port text: " + port + ". " + e, e);
        }
        spec.setPortalIP(hostName);
        return service;
    }

}
