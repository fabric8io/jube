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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ControllerCurrentState;
import io.fabric8.kubernetes.api.model.ControllerDesiredState;
import io.fabric8.kubernetes.api.model.Manifest;
import io.fabric8.kubernetes.api.model.ManifestContainer;
import io.fabric8.kubernetes.api.model.PodCurrentContainerInfo;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.PodTemplate;
import io.fabric8.kubernetes.api.model.PodTemplateDesiredState;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.hawt.util.Strings;
import io.jimagezip.process.ProcessManager;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Monitors the status of the current replication controllers and pods and chooses to start new pods if there are not enough replicas
 */
@Singleton
public class ReplicationManager {
    private static final transient Logger LOG = LoggerFactory.getLogger(ReplicationManager.class);

    private final LocalNodeModel model;
    private final ProcessManager processManager;
    private final long pollTime;
    private final Timer timer = new Timer();

    @Inject
    public ReplicationManager(LocalNodeModel model,
                              ProcessManager processManager,
                              @ConfigProperty(name = "autoScaler_pollTime", defaultValue = "2000")
                              long pollTime) {
        this.model = model;
        this.processManager = processManager;
        this.pollTime = pollTime;

        System.out.println("========= Starting the auto scaler with poll time: " + pollTime);

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                LOG.debug("autoscale timer");
                try {
                    autoScale();
                } catch (Exception e) {
                    System.out.println("Caught: " + e);
                    e.printStackTrace();
                    LOG.warn("Caught: " + e, e);
                }
            }
        };
        timer.schedule(timerTask, pollTime, pollTime);
    }

    protected void autoScale() throws Exception {
        ImmutableSet<Map.Entry<String, ReplicationControllerSchema>> entries = model.getReplicationControllerMap().entrySet();
        for (Map.Entry<String, ReplicationControllerSchema> entry : entries) {
            String rcID = entry.getKey();
            ReplicationControllerSchema replicationController = entry.getValue();
            PodTemplateDesiredState podTemplateDesiredState = NodeHelper.getPodTemplateDesiredState(replicationController);
            if (podTemplateDesiredState == null) {
                LOG.warn("Cannot instantiate replication controller: " + replicationController.getId() + " due to missing PodTemplate.DesiredState!");
                continue;
            }
            ControllerDesiredState desiredState = replicationController.getDesiredState();
            if (desiredState == null) {
                LOG.warn("Cannot instantiate replication controller: " + replicationController.getId() + " due to missing ControllerDesiredState!");
                continue;
            }
            Integer replicas = desiredState.getReplicas();
            ControllerCurrentState currentState = NodeHelper.getOrCreateCurrentState(replicationController);
            if (replicas != null && replicas.intValue() > 0) {
                int replicaCount = replicas.intValue();
                // TODO should we only show the running count?
                currentState.setReplicas(replicaCount);
                Map<String, String> replicaSelector = desiredState.getReplicaSelector();

                ImmutableList<PodSchema> pods = model.getPods(replicaSelector);


                // TODO should we delete dead pods?
                int createCount = replicaCount - pods.size();
                if (createCount > 0) {
                    createMissingContainers(replicationController, podTemplateDesiredState, desiredState, createCount);
                }
            }
        }
    }


    protected void createMissingContainers(ReplicationControllerSchema replicationController, PodTemplateDesiredState podTemplateDesiredState, ControllerDesiredState desiredState, int createCount) throws Exception {
        for (int i = 0; i < createCount; i++) {
            PodSchema pod = new PodSchema();

            createNewPod(replicationController, pod);
            List<ManifestContainer> containers = KubernetesHelper.getContainers(podTemplateDesiredState);
            for (ManifestContainer container : containers) {
                String containerName = pod.getId() + "-" + container.getName();
                PodCurrentContainerInfo containerInfo = NodeHelper.getOrCreateContainerInfo(pod, containerName);
                String image = container.getImage();
                if (Strings.isBlank(image)) {
                    LOG.warn("Missing image for " + containerName + " so cannot create it!");
                    continue;
                }
                NodeHelper.addOrUpdateDesiredContainer(pod, containerName, container);
            }
            PodTemplate podTemplate = desiredState.getPodTemplate();
            if (podTemplate != null) {
                pod.setLabels(podTemplate.getLabels());
            }
            // TODO should we update the pod now we've updated it?
            List<ManifestContainer> desiredContainers = NodeHelper.getOrCreatePodDesiredContainers(pod);
            NodeHelper.createMissingContainers(processManager, pod, NodeHelper.getOrCreateCurrentState(pod), desiredContainers);
        }
    }

    protected String createNewPod(ReplicationControllerSchema replicationController, PodSchema pod) {
        String id = replicationController.getId();
        if (Strings.isNotBlank(id)) {
            id += "-";
            int idx = 1;
            while (true) {
                String anId = id + (idx++);
                if (model.updatePodIfNotExist(anId, pod)) {
                    pod.setId(anId);
                    return null;
                }
            }
        }
        id = model.createID("Pod");
        pod.setId(id);
        return null;
    }

    @PreDestroy
    public void destroy() {
        if (timer != null) {
            timer.purge();
            timer.cancel();
        }
    }

    public long getPollTime() {
        return pollTime;
    }

    public LocalNodeModel getModel() {
        return model;
    }
}
