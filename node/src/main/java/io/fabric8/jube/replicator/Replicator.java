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
package io.fabric8.jube.replicator;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.fabric8.groups.Group;
import io.fabric8.groups.GroupListener;
import io.fabric8.groups.internal.ZooKeeperGroup;
import io.fabric8.jube.KubernetesModel;
import io.fabric8.jube.apimaster.ApiMasterKubernetesModel;
import io.fabric8.jube.apimaster.ApiMasterService;
import io.fabric8.jube.local.NodeHelper;
import io.fabric8.jube.process.ProcessManager;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.PodStatusType;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.ReplicationControllerStatus;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.utils.Closeables;
import io.fabric8.utils.Filter;
import io.fabric8.utils.Filters;
import io.fabric8.utils.Objects;
import io.hawt.util.Strings;
import org.apache.curator.framework.CuratorFramework;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.kubernetes.api.KubernetesHelper.getOrCreateMetadata;
import static io.fabric8.kubernetes.api.KubernetesHelper.getPodStatus;
import static io.fabric8.kubernetes.api.KubernetesHelper.setName;

/**
 * Monitors the status of the current replication controllers and pods and chooses to start new pods if there are not enough replicas
 */
@Singleton
public class Replicator {
    private static final transient Logger LOG = LoggerFactory.getLogger(Replicator.class);

    private static final String KUBERNETES_REPLICATOR = "/kubernetes/replicator";
    
    private final CuratorFramework curator;
    private final ApiMasterKubernetesModel model;
    private final ProcessManager processManager;
    private final long pollTime;
    private final Timer timer = new Timer();
    private final GroupListener<ReplicatorNode> groupListener;
    private ZooKeeperGroup<ReplicatorNode> group;
    private AtomicBoolean timerEnabled = new AtomicBoolean(false);
    private AtomicBoolean master = new AtomicBoolean(false);

    @Inject
    public Replicator(CuratorFramework curator,
                      ApiMasterKubernetesModel model,
                      ProcessManager processManager,
                      @ConfigProperty(name = "REPLICATOR_POLL_TIME", defaultValue = "2000")
                      long pollTime) {
        this.curator = curator;
        this.model = model;
        this.processManager = processManager;
        this.pollTime = pollTime;

        System.out.println("Starting the replicator with poll time: " + pollTime);

        group = new ZooKeeperGroup<ReplicatorNode>(curator, KUBERNETES_REPLICATOR, ReplicatorNode.class);
        groupListener = new GroupListener<ReplicatorNode>() {

            @Override
            public void groupEvent(Group<ReplicatorNode> group, GroupEvent event) {
                onGroupEvent(group, event);
            }
        };
        group.add(groupListener);
        group.update(createState());
        group.start();

        enableTimer();
    }

    @PreDestroy
    public void destroy() {
        disableTimer();
        group.remove(groupListener);
        Closeables.closeQuietly(group);
        group = null;
        disableTimer();
    }

    public boolean isMaster() {
        return group.isMaster() && master.get();
    }

    public void enableMaster() {
        if (master.compareAndSet(false, true)) {
            enableTimer();
            LOG.info("Replicator is the master");
            System.out.println("====== Replicator is the master");
            group.update(createState());
        }
    }

    protected void disableMaster() {
        if (master.compareAndSet(true, false)) {
            LOG.info("Replicator is not the master");
            System.out.println("====== Replicator is NOT the master");
            group.update(createState());
            disableTimer();
        }
    }

    protected void onGroupEvent(Group<ReplicatorNode> group, GroupListener.GroupEvent event) {
        switch (event) {
        case CONNECTED:
        case CHANGED:
            if (isValid()) {
                try {
                    if (group.isMaster()) {
                        enableMaster();
                    } else {
                        disableMaster();
                    }
                } catch (IllegalStateException e) {
                    // Ignore
                }
            } else {
                LOG.info("Not valid with master: " + group.isMaster()
                        + " curator: " + curator);
            }
            break;
        case DISCONNECTED:
        default:
        }
    }

    protected boolean isValid() {
        return true;
    }


    protected void autoScale() throws Exception {
        if (!isMaster()) {
            return;
        }
        ImmutableSet<Map.Entry<String, ReplicationController>> entries = model.getReplicationControllerMap().entrySet();
        for (Map.Entry<String, ReplicationController> entry : entries) {
            String rcID = entry.getKey();
            ReplicationController replicationController = entry.getValue();
            PodTemplateSpec podTemplateSpec = NodeHelper.getPodTemplateSpec(replicationController);
            if (podTemplateSpec == null) {
                LOG.warn("Cannot instantiate replication controller: " + getName(replicationController) + " due to missing PodTemplate.PodStatus!");
                continue;
            }
            int replicaCount = 0;
            ReplicationControllerSpec spec = replicationController.getSpec();
            if (spec != null) {
                Integer replicas = spec.getReplicas();
                if (replicas != null && replicas > 0) {
                    replicaCount = replicas;
                }
            }
            ReplicationControllerStatus currentState = NodeHelper.getOrCreatetStatus(replicationController);
            Map<String, String> replicaSelector = spec.getSelector();
            ImmutableList<Pod> allPods = model.getPods(replicaSelector);
            List<Pod> pods = Filters.filter(allPods, podHasNotTerminated());

            int currentSize = pods.size();
            Integer currentSizeInt = new Integer(currentSize);
            if (!Objects.equal(currentSizeInt, currentState.getReplicas())) {
                currentState.setReplicas(currentSizeInt);
                model.updateReplicationController(rcID, replicationController);
            }
            int createCount = replicaCount - currentSize;
            if (createCount > 0) {
                pods = createMissingContainers(replicationController, podTemplateSpec, spec, createCount, pods);
            } else if (createCount < 0) {
                int deleteCount = Math.abs(createCount);
                pods = deleteContainers(pods, deleteCount);
            }
        }
    }

    /**
     * Returns a filter of all terminated pods
     */
    public static Filter<Pod> podHasNotTerminated() {
        return new Filter<Pod>() {
            @Override
            public String toString() {
                return "PodHasNotTerminatedFilter";
            }

            @Override
            public boolean matches(Pod pod) {
                PodStatus currentState = pod.getStatus();
                if (currentState != null) {
                    PodStatusType podStatus = getPodStatus(pod);
                    switch (podStatus) {
                        case ERROR:
                            return  false;
                    }
/*
                    String status = currentState.getStatus();
                    if (status != null) {
                        String lower = status.toLowerCase();
                        if (lower.startsWith("error") || lower.startsWith("fail") || lower.startsWith("term")) {
                            return false;
                        }
                    }
*/
                }
                return true;
            }
        };
    }


    private ImmutableList<Pod> deleteContainers(List<Pod> pods, int deleteCount) throws Exception {
        List<Pod> list = Lists.newArrayList(pods);
        for (int i = 0, size = list.size(); i < deleteCount && i < size; i++) {
            Pod removePod = list.remove(size - i - 1);
            String id = getName(removePod);
            model.deleteRemotePod(removePod);
        }
        return ImmutableList.copyOf(list);
    }


    protected ImmutableList<Pod> createMissingContainers(ReplicationController replicationController, PodTemplateSpec podTemplateSpec,
                                                               ReplicationControllerSpec replicationControllerSpec, int createCount, List<Pod> pods) throws Exception {
        // TODO this is a hack ;) needs replacing with the real host we're creating on
        String host = ApiMasterService.getHostName();
        List<Pod> list = Lists.newArrayList(pods);
        for (int i = 0; i < createCount; i++) {
            Pod pod = new Pod();
            pod.setKind(NodeHelper.KIND_POD);

            createNewId(replicationController, pod);
            list.add(pod);

            List<Container> containers = KubernetesHelper.getContainers(podTemplateSpec);
            for (Container container : containers) {
                String containerName = getName(pod) + "-" + container.getName();

                ContainerStatus containerInfo = NodeHelper.getOrCreateContainerInfo(pod, containerName);
                PodStatus currentState = pod.getStatus();
                Objects.notNull(currentState, "currentState");
                currentState.setHostIP(host);

                String image = container.getImage();
                if (Strings.isBlank(image)) {
                    LOG.warn("Missing image for " + containerName + " so cannot create it!");
                    continue;
                }
                NodeHelper.addOrUpdateDesiredContainer(pod, containerName, container);
            }
            PodTemplateSpec podTemplate = replicationControllerSpec.getTemplate();
            if (podTemplate != null) {
                getOrCreateMetadata(pod).setLabels(KubernetesHelper.getLabels(podTemplate.getMetadata()));
            }
            // TODO should we update the pod now we've updated it?
            List<Container> desiredContainers = NodeHelper.getOrCreatePodDesiredContainers(pod);
            model.remoteCreatePod(pod);
        }
        return ImmutableList.copyOf(list);
    }

    protected String createNewId(ReplicationController replicationController, Pod pod) {
        String id = getName(replicationController);
        if (Strings.isNotBlank(id)) {
            id += "-";
            int idx = 1;
            while (true) {
                String anId = id + (idx++);
                if (model.updatePodIfNotExist(anId, pod)) {
                    setName(pod, anId);
                    return null;
                }
            }
        }
        id = model.createID(NodeHelper.KIND_POD);
        setName(pod, id);
        return null;
    }

    protected void enableTimer() {
        if (timerEnabled.compareAndSet(false, true)) {
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    LOG.debug("Replicator Timer");
                    try {
                        autoScale();
                    } catch (Exception e) {
                        System.out.println("Caught: " + e);
                        e.printStackTrace();
                        LOG.warn("Caught: " + e, e);
                    }
                }
            };
            timer.schedule(timerTask, this.pollTime, this.pollTime);
        }
    }

    protected void disableTimer() {
        System.out.println("disabling the Replicator timer!");
        timer.cancel();
        timerEnabled.set(false);
    }

    private ReplicatorNode createState() {
        ReplicatorNode state = new ReplicatorNode();
        return state;
    }

    public long getPollTime() {
        return pollTime;
    }

    public KubernetesModel getModel() {
        return model;
    }

}
