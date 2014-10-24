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
package org.jboss.jube.proxy;

import io.fabric8.kubernetes.api.model.CurrentState;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.hawt.util.Strings;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Represents a single service implementation in a container
 */
public class ContainerService {
    private final PodSchema pod;
    private final URI uri;

    public ContainerService(Service service, PodSchema pod) throws URISyntaxException {
        this.pod = pod;

        int port = service.getContainerPort();

        // lets get host / port of the container
        String host = null;
        CurrentState currentState = pod.getCurrentState();
        if (currentState != null) {
            host = currentState.getHost();
            if (Strings.isBlank(host)) {
                host = currentState.getPodIP();
            }
        }
        if (Strings.isBlank(host)) {
            throw new IllegalArgumentException("No host for pod " + pod.getId() + " so cannot use it with service " + service.getId());
        } else {
            uri = new URI("tcp://" + host + ":" + port);
        }
    }

    public URI getURI() {
        return uri;
    }
}
