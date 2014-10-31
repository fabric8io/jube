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
package org.jboss.jube.local;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.PodCurrentContainerInfo;
import io.fabric8.kubernetes.api.model.PodSchema;
import org.jboss.jube.KubernetesModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Represents a container in a pod in a model
 */
public class PodCurrentContainer {
    private static final transient Logger LOG = LoggerFactory.getLogger(PodCurrentContainer.class);

    private final KubernetesModel model;
    private final String podId;
    private final PodSchema pod;
    private final String containerId;
    private final PodCurrentContainerInfo currentContainer;

    public PodCurrentContainer(KubernetesModel model, String podId, PodSchema pod, String containerId, PodCurrentContainerInfo currentContainer) {
        this.model = model;
        this.podId = podId;
        this.pod = pod;
        this.containerId = containerId;
        this.currentContainer = currentContainer;
    }

    public KubernetesModel getModel() {
        return model;
    }

    public String getPodId() {
        return podId;
    }

    public PodSchema getPod() {
        return pod;
    }

    public String getContainerId() {
        return containerId;
    }

    public PodCurrentContainerInfo getCurrentContainer() {
        return currentContainer;
    }

    public void containerAlive(String id, boolean alive) {
        String oldJson = getPodJson();
        NodeHelper.containerAlive(pod, id, alive);
        String newJson = getPodJson();

        // lets only update the model if we've really changed the pod
        if (!Objects.equals(oldJson, newJson)) {
            model.updatePod(podId, pod);
        }
    }

    protected String getPodJson() {
        try {
            return KubernetesHelper.toJson(pod);
        } catch (JsonProcessingException e) {
            LOG.warn("Could not convert pod to json: " + e, e);
            return null;
        }
    }
}
