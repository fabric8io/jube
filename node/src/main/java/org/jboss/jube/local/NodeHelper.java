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
package org.jboss.jube.local;

import io.fabric8.common.util.Objects;
import io.fabric8.common.util.Strings;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ControllerCurrentState;
import io.fabric8.kubernetes.api.model.ControllerDesiredState;
import io.fabric8.kubernetes.api.model.CurrentState;
import io.fabric8.kubernetes.api.model.DesiredState;
import io.fabric8.kubernetes.api.model.Env;
import io.fabric8.kubernetes.api.model.Manifest;
import io.fabric8.kubernetes.api.model.ManifestContainer;
import io.fabric8.kubernetes.api.model.PodCurrentContainerInfo;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.PodTemplate;
import io.fabric8.kubernetes.api.model.PodTemplateDesiredState;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.Running;
import io.fabric8.kubernetes.api.model.State;
import io.hawt.aether.OpenMavenURL;
import org.jboss.jube.KubernetesModel;
import org.jboss.jube.process.InstallOptions;
import org.jboss.jube.process.Installation;
import org.jboss.jube.process.ProcessController;
import org.jboss.jube.process.ProcessManager;
import org.jboss.jube.util.ImageMavenCoords;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A set of helper functions for implementing the local node
 */
public class NodeHelper {
    public static final String KIND_POD = "Pod";
    public static final String KIND_REPLICATION_CONTROLLER = "ReplicationController";
    public static final String KIND_SERVICE = "SERVICE";

    /**
     * Returns the desired state; lazily creating one if required
     */
    public static DesiredState getOrCreateDesiredState(PodSchema pod) {
        Objects.notNull(pod, "pod");
        DesiredState desiredState = pod.getDesiredState();
        if (desiredState == null) {
            desiredState = new DesiredState();
            pod.setDesiredState(desiredState);
        }
        return desiredState;
    }

    /**
     * Returns the current state of the given pod; lazily creating one if required
     */
    public static CurrentState getOrCreateCurrentState(PodSchema pod) {
        Objects.notNull(pod, "pod");
        CurrentState currentState = pod.getCurrentState();
        if (currentState == null) {
            currentState = new CurrentState();
            pod.setCurrentState(currentState);
        }
        return currentState;
    }

    public static PodTemplateDesiredState getPodTemplateDesiredState(ReplicationControllerSchema replicationController) {
        if (replicationController != null) {
            return getPodTemplateDesiredState(replicationController.getDesiredState());
        }
        return null;
    }

    public static PodTemplateDesiredState getPodTemplateDesiredState(ControllerDesiredState desiredState) {
        PodTemplate podTemplate = null;
        PodTemplateDesiredState podTemplateDesiredState = null;
        if (desiredState != null) {
            podTemplate = desiredState.getPodTemplate();
            if (podTemplate != null) {
                podTemplateDesiredState = podTemplate.getDesiredState();
            }
        }
        return podTemplateDesiredState;
    }

    /**
     * Returns the current container map for the current pod state; lazily creating if required
     */
    public static Map<String, PodCurrentContainerInfo> getOrCreateCurrentContainerInfo(PodSchema pod) {
        CurrentState currentState = getOrCreateCurrentState(pod);
        Map<String, PodCurrentContainerInfo> info = currentState.getInfo();
        if (info == null) {
            info = new HashMap<>();
            currentState.setInfo(info);
        }
        return info;
    }

    /**
     * Returns the containers state, lazily creating any objects if required.
     */
    public static State getOrCreateContainerState(PodSchema pod, String containerName) {
        PodCurrentContainerInfo containerInfo = getOrCreateContainerInfo(pod, containerName);
        State state = containerInfo.getState();
        if (state == null) {
            state = new State();
            containerInfo.setState(state);
        }
        return state;
    }

    /**
     * Returns the container information for the given pod and container name, lazily creating as required
     */
    public static PodCurrentContainerInfo getOrCreateContainerInfo(PodSchema pod, String containerName) {
        Map<String, PodCurrentContainerInfo> map = getOrCreateCurrentContainerInfo(pod);
        PodCurrentContainerInfo containerInfo = map.get(containerName);
        if (containerInfo == null) {
            containerInfo = new PodCurrentContainerInfo();
            map.put(containerName, containerInfo);
        }
        return containerInfo;
    }

    /**
     * Creates any missing containers; updating the currentState with the new values.
     */
    public static String createMissingContainers(ProcessManager processManager, PodSchema pod, CurrentState currentState, List<ManifestContainer> containers) throws Exception {
        Map<String, PodCurrentContainerInfo> currentContainers = KubernetesHelper.getCurrentContainers(currentState);

        for (ManifestContainer container : containers) {
            // TODO check if we already have a working container
            createContainer(processManager, container, pod, currentState);
        }
        return null;
    }

    /**
     * Converts a possibly null list of Env objects into a Map of environment variables
     */
    public static Map<String,String> createEnvironmentVariableMap(List<Env> envList) {
        Map<String, String> answer = new HashMap<>();
        if (envList != null) {
            for (Env env : envList) {
                String name = env.getName();
                String value = env.getValue();
                if (Strings.isNotBlank(name)) {
                    answer.put(name, value);
                }
            }
        }
        return answer;
    }

    protected static void createContainer(ProcessManager processManager, ManifestContainer container, PodSchema pod, CurrentState currentState) throws Exception {
        String containerName = container.getName();
        String image = container.getImage();
        Strings.notEmpty(image);
        OpenMavenURL mavenUrl = ImageMavenCoords.dockerImageToMavenURL(image);
        Objects.notNull(mavenUrl, "mavenUrl");

        System.out.println("Creating new container " + containerName + " from: " + mavenUrl);
        Map<String,String> envVarMap = createEnvironmentVariableMap(container.getEnv());
        System.out.println("Env variables are: " + envVarMap);
        InstallOptions.InstallOptionsBuilder builder = new InstallOptions.InstallOptionsBuilder().
                url(mavenUrl).environment(envVarMap);
        if (Strings.isNotBlank(containerName)) {
            builder = builder.name(containerName).id(containerName);
        }
        InstallOptions installOptions = builder.build();

        Installation installation = processManager.install(installOptions, null);
        File installDir = installation.getInstallDir();

        PodCurrentContainerInfo containerInfo = NodeHelper.getOrCreateContainerInfo(pod, containerName);

        System.out.println("Installed new process at: " + installDir);

        // TODO add a container to the current state
        ProcessController controller = installation.getController();
        controller.start();

        Long pid = controller.getPid();
        containerAlive(pod, containerName, pid != null && pid.longValue() > 0);

        System.out.println("Started the process!");
    }

    public static void deleteContainers(ProcessManager processManager, PodSchema pod, CurrentState currentState, List<ManifestContainer> desiredContainers) throws Exception {
        for (ManifestContainer container : desiredContainers) {
            deleteContainer(processManager, container, pod, currentState);
        }
    }

    protected static void deleteContainer(ProcessManager processManager, ManifestContainer container, PodSchema pod, CurrentState currentState) throws Exception {
        String containerName = container.getName();
        Installation installation = processManager.getInstallation(containerName);
        if (installation == null) {
            System.out.println("No such container: " + containerName);
            return;
        }
        ProcessController controller = installation.getController();
        controller.kill();
        controller.uninstall();
    }




    public static void containerAlive(PodSchema pod, String id, boolean alive) {
         CurrentState currentState = getOrCreateCurrentState(pod);
         if (alive) {
             currentState.setStatus("Running");
         } else {
             currentState.setStatus("Waiting");
         }
         State state = getOrCreateContainerState(pod, id);
         if (alive) {
             Running running = new Running();
             state.setRunning(running);
         } else {
             state.setRunning(null);
         }
     }

    public static ManifestContainer addOrUpdateDesiredContainer(PodSchema pod, String containerName, ManifestContainer container) {
        List<ManifestContainer> containers = getOrCreatePodDesiredContainers(pod);
        ManifestContainer oldContainer = findContainer(containers, containerName);
        if (oldContainer != null) {
            // lets update it just in case something changed...
            containers.remove(oldContainer);
        }
        ManifestContainer newContainer = new ManifestContainer();

        // TODO we should use bean utils or something to copy properties in case we miss one!
        newContainer.setCommand(container.getCommand());
        newContainer.setEnv(container.getEnv());
        newContainer.setImage(container.getImage());
        newContainer.setPorts(container.getPorts());
        newContainer.setVolumeMounts(container.getVolumeMounts());
        newContainer.setWorkingDir(container.getWorkingDir());
        newContainer.getAdditionalProperties().putAll(container.getAdditionalProperties());
        newContainer.setName(containerName);
        System.out.println("Added new container: " + containerName);

        containers.add(newContainer);
        return newContainer;
    }

    public static List<ManifestContainer> getOrCreatePodDesiredContainers(PodSchema pod) {
        DesiredState podDesiredState = NodeHelper.getOrCreateDesiredState(pod);
        Manifest manifest = podDesiredState.getManifest();
        if (manifest == null) {
            manifest = new Manifest();
            podDesiredState.setManifest(manifest);
        }
        List<ManifestContainer> containers = manifest.getContainers();
        if (containers == null) {
            containers = new ArrayList<>();
            manifest.setContainers(containers);
        } return containers;
    }

    public static ManifestContainer findContainer(List<ManifestContainer> containers, String name) {
        for (ManifestContainer container : containers) {
            if (Objects.equal(container.getName(), name)) {
                return container;
            }
        }
        return null;
    }

    public static ControllerCurrentState getOrCreateCurrentState(ReplicationControllerSchema replicationController) {
        ControllerCurrentState currentState = replicationController.getCurrentState();
        if (currentState == null) {
            currentState = new ControllerCurrentState();
            replicationController.setCurrentState(currentState);
        }
        return currentState;
    }

    public static void deletePod(ProcessManager processManager, KubernetesModel model, String podId) throws Exception {
        PodSchema pod = model.deletePod(podId);
        if (pod != null) {
            List<ManifestContainer> desiredContainers = NodeHelper.getOrCreatePodDesiredContainers(pod);
            NodeHelper.deleteContainers(processManager, pod, NodeHelper.getOrCreateCurrentState(pod), desiredContainers);
        }
    }
}
