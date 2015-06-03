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

import io.fabric8.kubernetes.api.KubernetesClient;
import io.fabric8.kubernetes.api.KubernetesFactory;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * A simple client for working with {@link KubernetesExtensions}
 */
public class KubernetesExtensionsClient extends KubernetesClient implements KubernetesExtensions {
    private KubernetesExtensions extensions;

    public KubernetesExtensionsClient() {
    }

    public KubernetesExtensionsClient(String url) {
        this(createJubeFactory(url));
    }

    public static KubernetesFactory createJubeFactory(String address) {
        return new KubernetesFactory(address, false);
    }

    public KubernetesExtensionsClient(KubernetesFactory factory) {
        super(factory);
    }

    public KubernetesExtensions getExtensions() {
        if (extensions == null) {
            extensions = getFactory().createWebClient(KubernetesExtensions.class);
        }
        return extensions;
    }

    // Delegate API
    //-------------------------------------------------------------------------


    public String createLocalPod(Pod entity) throws Exception {
        return getExtensions().createLocalPod(entity);
    }

    @GET
    @Path("local/pods")
    @Consumes("application/json")
    public PodList getLocalPods() {
        return getExtensions().getLocalPods();
    }

    @DELETE
    @Path("local/pods/{id}")
    @Consumes("text/plain")
    public String deleteLocalPod(@NotNull String id, String namespace) throws Exception {
        return getExtensions().deleteLocalPod(id, namespace);
    }
}
