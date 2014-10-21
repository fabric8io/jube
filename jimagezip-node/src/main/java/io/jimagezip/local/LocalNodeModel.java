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

import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.hawt.util.Strings;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.UUID.randomUUID;

/**
 */
@Singleton
public class LocalNodeModel {
    private Map<String, PodSchema> podMap = new ConcurrentHashMap<>();
    private Map<String, ReplicationControllerSchema> replicationControllerSchemaMap = new ConcurrentHashMap<>();
    private Map<String, ServiceSchema> serviceMap = new ConcurrentHashMap<>();

    @Inject
    public LocalNodeModel() {
    }

    public PodSchema getPod(String id) {
        return podMap.get(id);
    }

    public ReplicationControllerSchema getReplicationController(String id) {
        return replicationControllerSchemaMap.get(id);
    }

    public ServiceSchema getService(String id) {
        return serviceMap.get(id);
    }

    /**
     * Creates a new ID for the given kind
     */
    public String createID(String kind) {
        return kind + "-" + randomUUID().toString();
    }

    public void updateReplicationController(String controllerId, ReplicationControllerSchema replicationController) {
        if (Strings.isBlank(controllerId)) {
            controllerId = createID("ReplicationController");
        }
        replicationControllerSchemaMap.put(controllerId, replicationController);
    }
}
