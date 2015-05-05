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
package io.fabric8.jube.local;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableSet;
import io.fabric8.jube.KubernetesModel;
import io.fabric8.jube.Statuses;
import io.fabric8.jube.apimaster.ApiMasterService;
import io.fabric8.jube.process.InstallOptions;
import io.fabric8.jube.process.Installation;
import io.fabric8.jube.process.ProcessController;
import io.fabric8.jube.process.ProcessManager;
import io.fabric8.jube.util.ImageMavenCoords;
import io.fabric8.jube.util.InstallHelper;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.PodStatusType;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStateRunning;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.ReplicationControllerStatus;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import io.hawt.aether.OpenMavenURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.kubernetes.api.KubernetesHelper.getPorts;

/**
 * A set of helper functions for implementing the local node
 */
public final class NodeHelper {
    public static final String KIND_POD = "Pod";
    public static final String KIND_REPLICATION_CONTROLLER = "ReplicationController";
    public static final String KIND_SERVICE = "SERVICE";

    // use 60 second stop timeout by default
    private static final int STOP_TIMEOUT = 60;

    private static final transient Logger LOG = LoggerFactory.getLogger(NodeHelper.class);

    private NodeHelper() {
        // utility class
    }

    /**
     * Returns the desired state; lazily creating one if required
     */
    public static PodSpec getOrCreatePodSpec(Pod pod) {
        Objects.notNull(pod, "pod");
        PodSpec desiredState = pod.getSpec();
        if (desiredState == null) {
            desiredState = new PodSpec();
            pod.setSpec(desiredState);
        }
        return desiredState;
    }

    /**
     * Returns the current state of the given pod; lazily creating one if required
     */
    public static PodStatus getOrCreatetStatus(Pod pod) {
        Objects.notNull(pod, "pod");
        PodStatus currentState = pod.getStatus();
        if (currentState == null) {
            currentState = new PodStatus();
            pod.setStatus(currentState);
        }
        return currentState;
    }

    public static PodTemplateSpec getPodTemplateSpec(ReplicationController replicationController) {
        if (replicationController != null) {
            return getPodTemplateSpec(replicationController.getSpec());
        }
        return null;
    }

    public static PodTemplateSpec getPodTemplateSpec(ReplicationControllerSpec replicationControllerSpec) {
        PodTemplateSpec podTemplate = null;
        if (replicationControllerSpec != null) {
            podTemplate = replicationControllerSpec.getTemplate();
        }
        return podTemplate;
    }

    public static PodSpec getPodTemplatePodSpec(ReplicationController replicationController) {
        if (replicationController != null) {
            return getPodTemplatePodSpec(replicationController.getSpec());
        }
        return null;
    }

    public static PodSpec getPodTemplatePodSpec(ReplicationControllerSpec replicationControllerSpec) {
        PodTemplateSpec podTemplate;
        PodSpec podTemplatePodStatus = null;
        if (replicationControllerSpec != null) {
            podTemplate = replicationControllerSpec.getTemplate();
            if (podTemplate != null) {
                podTemplatePodStatus = podTemplate.getSpec();
            }
        }
        return podTemplatePodStatus;
    }

    /**
     * Returns the current container map for the current pod state; lazily creating if required
     */
    public static Map<String, ContainerStatus> getOrCreateCurrentContainerInfo(Pod pod) {
        PodStatus currentState = getOrCreatetStatus(pod);
        Map<String, ContainerStatus> info = currentState.getInfo();
        if (info == null) {
            info = new HashMap<>();
            currentState.setInfo(info);
        }
        return info;
    }

    /**
     * Returns the containers state, lazily creating any objects if required.
     */
    public static ContainerState getOrCreateContainerState(Pod pod, String containerName) {
        ContainerStatus containerInfo = getOrCreateContainerInfo(pod, containerName);
        ContainerState state = containerInfo.getState();
        if (state == null) {
            state = new ContainerState();
            containerInfo.setState(state);
        }
        return state;
    }

    /**
     * Returns the container information for the given pod and container name, lazily creating as required
     */
    public static ContainerStatus getOrCreateContainerInfo(Pod pod, String containerName) {
        Map<String, ContainerStatus> map = getOrCreateCurrentContainerInfo(pod);
        ContainerStatus containerInfo = map.get(containerName);
        if (containerInfo == null) {
            containerInfo = new ContainerStatus();
            map.put(containerName, containerInfo);
        }
        return containerInfo;
    }

    /**
     * Creates any missing containers; updating the currentState with the new values.
     */
    public static String createMissingContainers(final ProcessManager processManager, final KubernetesModel model, final Pod pod,
                                                 final PodStatus currentState, List<Container> containers) throws Exception {
        Map<String, ContainerStatus> currentContainers = KubernetesHelper.getCurrentContainers(currentState);

        for (final Container container : containers) {
            // lets update the pod model if we update the ports
            podTransaction(model, pod, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    createContainer(processManager, model, container, pod, currentState);
                    return null;
                }
            });
        }

        return null;
    }

    /**
     * Converts a possibly null list of Env objects into a Map of environment variables
     */
    public static Map<String, String> createEnvironmentVariableMap(List<EnvVar> envList) {
        Map<String, String> answer = new HashMap<>();
        if (envList != null) {
            for (EnvVar env : envList) {
                String name = env.getName();
                String value = env.getValue();
                if (Strings.isNotBlank(name)) {
                    answer.put(name, value);
                }
            }
        }
        return answer;
    }


    public static List<EnvVar> createEnvironmentVariables(Map<String, String> environment) {
        List<EnvVar> answer = new ArrayList<>();
        Set<Map.Entry<String, String>> entries = environment.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (Strings.isNotBlank(key)) {
                EnvVar env = new EnvVar();
                env.setName(key);
                env.setValue(value);
                answer.add(env);
            }
        }
        return answer;
    }

    protected static void createContainer(ProcessManager processManager, KubernetesModel model, Container container, Pod pod, PodStatus currentState) throws Exception {
        String containerName = container.getName();
        String image = container.getImage();
        Strings.notEmpty(image);
        OpenMavenURL mavenUrl = ImageMavenCoords.dockerImageToMavenURL(image);
        Objects.notNull(mavenUrl, "mavenUrl");

        LOG.info("Creating new container: {} from url: {}", containerName, mavenUrl);

        Map<String, String> envVarMap = createEnvironmentVariableMap(container.getEnv());
        // now lets copy in the service env vars...
        appendServiceEnvironmentVariables(envVarMap, model);
        LOG.info("Env variables are: {}", envVarMap);
        InstallOptions.InstallOptionsBuilder builder = new InstallOptions.InstallOptionsBuilder().
                url(mavenUrl).environment(envVarMap);
        if (Strings.isNotBlank(containerName)) {
            builder = builder.name(containerName).id(containerName);
        }
        InstallOptions installOptions = builder.build();

        try {
            Installation installation;
            try {
                installation = processManager.install(installOptions, null);
            } catch (IOException ioe) {
                LOG.debug("Cannot find image at {} - trying with default prefix", mavenUrl);
                mavenUrl = ImageMavenCoords.dockerImageToMavenURL(image, true);
                Objects.notNull(mavenUrl, "mavenUrl");
                builder = new InstallOptions.InstallOptionsBuilder().
                        url(mavenUrl).environment(envVarMap);
                if (Strings.isNotBlank(containerName)) {
                    builder = builder.name(containerName).id(containerName);
                }
                installOptions = builder.build();
                installation = processManager.install(installOptions, null);
            }
            File installDir = installation.getInstallDir();

            ContainerStatus containerInfo = NodeHelper.getOrCreateContainerInfo(pod, containerName);

            LOG.info("Installed new process in directory: {}", installDir);
            ProcessController controller = installation.getController();
            Map<String, String> environment = controller.getConfig().getEnvironment();
            container.setEnv(createEnvironmentVariables(environment));
            createInstallationPorts(environment, installation, container);
            model.updatePod(getName(pod), pod);

            // TODO add a container to the current state
            LOG.info("Staring container: {}", containerName);
            controller.start();
            Long pid = controller.getPid();

            boolean alive = pid != null && pid > 0;
            LOG.info("Container: {} has pid: {} and is alive: {}", containerName, pid != null ? pid : "<null>", alive);

            containerAlive(pod, containerName, alive);
        } catch (Exception e) {
            currentState.setStatus("Terminated: " + e);
            System.out.println("ERROR: Failed to create pod: " + getName(pod) + ". " + e);
            e.printStackTrace();
            LOG.error("Failed to create pod: " + getName(pod) + ". " + e.getMessage(), e);
        }
    }

    protected static List<ContainerPort> createInstallationPorts(Map<String, String> environment, Installation installation, Container container) {
        try {
            List<ContainerPort> answer = container.getPorts();
            if (answer == null) {
                answer = new ArrayList<>();
                container.setPorts(answer);
            }
            File installDir = installation.getInstallDir();
            Map<String, String> portMap = InstallHelper.readPortsFromDirectory(installDir);
            Set<Map.Entry<String, String>> entries = portMap.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String key = entry.getKey();
                String value = entry.getValue();
                String containerPort = value;
                String envVarName = InstallHelper.portNameToHostEnvVarName(key);
                String hostPort = environment.get(envVarName);
                if (Strings.isNullOrBlank(hostPort)) {
                    LOG.warn("Cannot find env var value for $" + envVarName + " so cannot define the host port");
                } else {
                    int containerPortNumber = toPortNumber(value, key, "container");
                    int hostPortNumber = toPortNumber(hostPort, key, "host");
                    if (containerPortNumber > 0) {
                        // lets find a port object for the given number
                        ContainerPort port = findPortWithContainerPort(answer, containerPortNumber);
                        if (port == null) {
                            port = new ContainerPort();
                            port.setContainerPort(containerPortNumber);
                            answer.add(port);
                        }
                        port.setName(key);
                        if (hostPortNumber > 0) {
                            port.setHostPort(hostPortNumber);
                        }
                    }
                }
            }
            return answer;
        } catch (IOException e) {
            LOG.warn("Failed to load ports for installation " + installation + ". " + e.getMessage(), e);
            return null;
        }
    }

    public static ContainerPort findPortWithContainerPort(List<ContainerPort> ports, int containerPortNumber) {
        for (ContainerPort port : ports) {
            Integer containerPort = port.getContainerPort();
            if (containerPort != null && containerPort == containerPortNumber) {
                return port;
            }
        }
        return null;
    }

    protected static int toPortNumber(String text, String key, String description) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            LOG.warn("Failed to parse port text '" + text + "' for port " + key + " " + description + ". " + e, e);
            return -1;
        }
    }

    public static int findHostPortForService(Pod pod, int serviceContainerPort) {
        List<Container> containers = KubernetesHelper.getContainers(pod);
        for (Container container : containers) {
            List<ContainerPort> ports = container.getPorts();
            if (ports != null) {
                for (ContainerPort port : ports) {
                    Integer containerPort = port.getContainerPort();
                    Integer hostPort = port.getHostPort();
                    if (containerPort != null && containerPort == serviceContainerPort) {
                        if (hostPort != null) {
                            return hostPort;
                        }
                    }
                }
            }
        }
        LOG.warn("Could not find host port for service port: {} pod: {}", serviceContainerPort, pod);
        return serviceContainerPort;
    }

    public static void appendServiceEnvironmentVariables(Map<String, String> map, KubernetesModel model) {
        ImmutableSet<Map.Entry<String, Service>> entries = model.getServiceMap().entrySet();
        for (Map.Entry<String, Service> entry : entries) {
            String id = entry.getKey().toUpperCase().replaceAll("-", "_");
            String envVarPrefix = id + "_SERVICE_";
            Service service = entry.getValue();

            // TODO should we allow different service ports?
            String host = ApiMasterService.getHostName();
            Set<Integer> ports = getPorts(service);
            for (Integer port : ports) {
                String hostEnvVar = envVarPrefix + "HOST";
                String portEnvVar = envVarPrefix + "PORT";

                map.put(hostEnvVar, host);
                map.put(portEnvVar, "" + port);
            }
        }
    }

    public static void deleteContainers(ProcessManager processManager, KubernetesModel model, Pod pod, PodStatus currentState, List<Container> desiredContainers) throws Exception {
        for (Container container : desiredContainers) {
            deleteContainer(processManager, model, container, pod, currentState);
        }
    }

    protected static void deleteContainer(ProcessManager processManager, KubernetesModel model, Container container, Pod pod, PodStatus currentState) throws Exception {
        String containerName = container.getName();
        Installation installation = processManager.getInstallation(containerName);
        if (installation == null) {
            LOG.info("Cannot delete non existing container: {}", containerName);
            return;
        }

        ProcessController controller = installation.getController();

        boolean kill = false;

        // try graceful to stop first, then kill afterwards
        // as the controller may issue a command that stops asynchronously, we need to check if the pid is alive
        // until its graceful shutdown, before we go harder and try to kill it
        try {
            LOG.info("Stopping container: {}", containerName);
            controller.stop();
        } catch (Exception e) {
            kill = true;
            LOG.warn("Error during stopping container: " + containerName + ". This exception is ignored", e);
        }

        // if the process was alive then kill is not needed
        boolean stopped = false;
        for (int i = 0; i < STOP_TIMEOUT; i++) {
            if (i > 0) {
                LOG.info("Waiting for {} seconds to graceful stop container: {}", i, containerName);
            }
            // check if the process has been stopped graceful
            if (!safeCheckIsAlive(installation)) {
                stopped = true;
                break;
            } else {
                // wait 1 sec
                Thread.sleep(1000);
            }
        }
        if (!stopped) {
            LOG.warn("Cannot graceful stop container: {} after {} seconds. Will now forcibly shutdown the container.", containerName, STOP_TIMEOUT);
        }

        // if we did not stop graceful then do a kill
        if (kill || !stopped) {
            try {
                LOG.info("Killing container: {}", containerName);
                controller.kill();
            } catch (Exception e) {
                LOG.warn("Error during killing container: " + containerName + ". This exception is ignored", e);
            }
        }

        try {
            LOG.info("Uninstalling container: {}", containerName);
            controller.uninstall();
        } catch (Exception e) {
            LOG.warn("Error during uninstalling container: " + containerName + ". This exception is ignored", e);
        }

        try {
            LOG.info("Deleting pod: {}", getName(pod));
            model.deletePod(getName(pod), pod.getNamespace());
        } catch (Exception e) {
            // ignore
            LOG.warn("Error during deleting pod: " + getName(pod) + ". This exception is ignored", e);
        }

        LOG.info("Deleted container: {} done", containerName);
    }

    private static boolean safeCheckIsAlive(Installation installation) {
        Long pid = 0L;
        try {
            pid = installation.getActivePid();
        } catch (Exception e) {
            // ignore, but force a pid value so we run for the timeout duration
        }
        return pid != null && pid.longValue() > 0;
    }


    public static void containerAlive(Pod pod, String id, boolean alive) {
        PodStatus currentState = getOrCreatetStatus(pod);
        String status = currentState.getStatus();
        if (alive) {
            currentState.setStatus(Statuses.RUNNING);
        } else {
            if (Strings.isNullOrBlank(status)) {
                currentState.setStatus(Statuses.WAITING);
            } else if (!Objects.equal(Statuses.WAITING, status)) {
                currentState.setStatus(Statuses.TERMINATED);
            }
        }
        setContainerRunningState(pod, id, alive);
    }

    public static void setPodStatus(Pod pod, String status) {
        PodStatus currentState = getOrCreatetStatus(pod);
        currentState.setStatus(status);
    }

    public static void setContainerRunningState(Pod pod, String id, boolean alive) {
        ContainerState state = getOrCreateContainerState(pod, id);
        if (alive) {
            ContainerStateRunning running = new ContainerStateRunning();
            state.setRunning(running);
        } else {
            state.setRunning(null);
        }
    }

    public static Container addOrUpdateDesiredContainer(Pod pod, String containerName, Container container) {
        List<Container> containers = getOrCreatePodDesiredContainers(pod);
        Container oldContainer = findContainer(containers, containerName);
        if (oldContainer != null) {
            // lets update it just in case something changed...
            containers.remove(oldContainer);
        }
        Container newContainer = new Container();

        // TODO we should use bean utils or something to copy properties in case we miss one!
        newContainer.setCommand(container.getCommand());
        newContainer.setEnv(container.getEnv());
        newContainer.setImage(container.getImage());
        newContainer.setPorts(container.getPorts());
        newContainer.setVolumeMounts(container.getVolumeMounts());
        newContainer.setWorkingDir(container.getWorkingDir());
        newContainer.getAdditionalProperties().putAll(container.getAdditionalProperties());
        newContainer.setName(containerName);
        LOG.info("Added new container: {}", containerName);

        containers.add(newContainer);
        return newContainer;
    }

    public static List<Container> getOrCreatePodDesiredContainers(Pod pod) {
        PodSpec podSpec = NodeHelper.getOrCreatePodSpec(pod);
        List<Container> containers = podSpec.getContainers();
        if (containers == null) {
            containers = new ArrayList<>();
            podSpec.setContainers(containers);
        }
        return containers;
    }

    public static Container findContainer(List<Container> containers, String name) {
        for (Container container : containers) {
            if (Objects.equal(container.getName(), name)) {
                return container;
            }
        }
        return null;
    }

    public static ReplicationControllerStatus getOrCreatetStatus(ReplicationController replicationController) {
        ReplicationControllerStatus currentState = replicationController.getStatus();
        if (currentState == null) {
            currentState = new ReplicationControllerStatus();
            replicationController.setStatus(currentState);
        }
        return currentState;
    }

    public static void deletePod(ProcessManager processManager, KubernetesModel model, String podId, String namespace) throws Exception {
        Pod pod = model.deletePod(podId, namespace);
        if (pod != null) {
            List<Container> desiredContainers = NodeHelper.getOrCreatePodDesiredContainers(pod);
            NodeHelper.deleteContainers(processManager, model, pod, NodeHelper.getOrCreatetStatus(pod), desiredContainers);
        }
    }

    /**
     * Performs a block of code and updates the pod model if its updated
     */
    public static void podTransaction(KubernetesModel model, Pod pod, Runnable task) {
        String oldJson = getPodJson(pod);
        task.run();
        String newJson = getPodJson(pod);

        // lets only update the model if we've really changed the pod
        if (!java.util.Objects.equals(oldJson, newJson)) {
            model.updatePod(getName(pod), pod);
        }
    }

    /**
     * Performs a block of code and updates the pod model if its updated
     */
    public static <T> T podTransaction(KubernetesModel model, Pod pod, Callable<T> task) throws Exception {
        String oldJson = getPodJson(pod);
        T answer = task.call();
        String newJson = getPodJson(pod);

        // lets only update the model if we've really changed the pod
        if (!java.util.Objects.equals(oldJson, newJson)) {
            model.updatePod(getName(pod), pod);
        }
        return answer;
    }


    /**
     * Performs a block of code and updates the pod model if its updated
     */
    public static void excludeFromProcessMonitor(ProcessMonitor monitor, Pod pod, Runnable task) {
        String id = getName(pod);
        monitor.addExcludedPodId(id);
        try {
            task.run();
        } finally {
            monitor.removeExcludedPodId(id);
        }
    }

    /**
     * Performs a block of code and updates the pod model if its updated
     */
    public static <T> T excludeFromProcessMonitor(ProcessMonitor monitor, Pod pod,  Callable<T> task) throws Exception {
        String id = getName(pod);
        monitor.addExcludedPodId(id);
        try {
            return task.call();
        } finally {
            monitor.removeExcludedPodId(id);
        }
    }


    protected static String getPodJson(Pod pod) {
        try {
            return KubernetesHelper.toJson(pod);
        } catch (JsonProcessingException e) {
            LOG.warn("Could not convert pod to json: " + e, e);
            return null;
        }
    }

    /**
     * Returns true if there has been a change in the JSON of the given entity
     */
    public static boolean podHasChanged(Pod currentEntity, Pod oldEntity) {
        if (currentEntity == null || oldEntity == null) {
            return true;
        }
        String oldJson = getPodJson(oldEntity);
        String newJson = getPodJson(currentEntity);
        return !java.util.Objects.equals(oldJson, newJson);
    }
}
