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

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import io.fabric8.kubernetes.api.Kubernetes;
import io.fabric8.kubernetes.api.model.PodListSchema;
import io.fabric8.kubernetes.api.model.PodSchema;

/**
 */
public interface KubernetesExtensions extends Kubernetes {
    @GET
    @Path("local/pods")
    @Consumes("application/json")
    PodListSchema getLocalPods();

    @POST
    @Path("local/pods")
    @Consumes("application/json")
    String createLocalPod(PodSchema entity) throws Exception;

    @DELETE
    @Path("local/pods/{id}")
    @Consumes("text/plain")
    String deleteLocalPod(@PathParam("id") @NotNull String id) throws Exception;

}
