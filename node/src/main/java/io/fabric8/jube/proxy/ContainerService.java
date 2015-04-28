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
package io.fabric8.jube.proxy;

import java.net.URI;
import java.net.URISyntaxException;

import io.fabric8.jube.local.NodeHelper;
import io.fabric8.kubernetes.api.model.PodState;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.hawt.util.Strings;

/**
 * Represents a single service implementation in a container
 */
public class ContainerService {
    
    private final ServicePort servicePort;
    private final Pod pod;
    private final URI uri;

    public ContainerService(ServicePort servicePort, Pod pod) throws URISyntaxException {
        this.servicePort = servicePort;
        this.pod = pod;
        int serviceContainerPort = servicePort.getContainerPort().getIntVal();
        int port = NodeHelper.findHostPortForService(pod, serviceContainerPort);

        // lets get host / port of the container
        String host = null;
        PodState currentState = pod.getCurrentState();
        if (currentState != null) {
            host = currentState.getHost();
            if (Strings.isBlank(host)) {
                host = currentState.getPodIP();
            }
        }
        if (Strings.isBlank(host)) {
            throw new IllegalArgumentException("No host for pod " + pod.getId() + " so cannot use it with service port: " + servicePort.getName());
        } else {
            uri = new URI("tcp://" + host + ":" + port);
        }
    }
    
    public String getName() {
        return servicePort.getName();
    }

    public URI getURI() {
        return uri;
    }
    
    

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ContainerService that = (ContainerService) o;

        if (pod != null ? !pod.equals(that.pod) : that.pod != null) return false;
        if (servicePort != null ? !servicePort.equals(that.servicePort) : that.servicePort != null) return false;
        if (uri != null ? !uri.equals(that.uri) : that.uri != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = servicePort != null ? servicePort.hashCode() : 0;
        result = 31 * result + (pod != null ? pod.hashCode() : 0);
        result = 31 * result + (uri != null ? uri.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ContainerService{" +
                "servicePort=" + servicePort +
                ", pod=" + pod +
                ", uri=" + uri +
                '}';
    }
}
