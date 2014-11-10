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
package io.fabric8.jube.registry;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import io.fabric8.cxf.endpoint.EnableJMXFeature;
import org.apache.cxf.feature.LoggingFeature;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@ApplicationPath("/facade")
public class Registry extends Application {

    @Produces
    private JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider();

    @Inject
    private RegistryFacadeService registryFacadeService;

    public Registry() {
        System.out.println("==================== started NodeApplication");
    }

    @Override
    public Set<Object> getSingletons() {
        return new HashSet<Object>(
                Arrays.asList(
                        registryFacadeService,
                        jacksonJsonProvider,
                        new EnableJMXFeature(),
                        new LoggingFeature()
                )
        );
    }
}

