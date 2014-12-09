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
package io.fabric8.jube.process.service;

import javax.inject.Singleton;

import io.fabric8.jube.util.JubeVersionUtils;
import org.apache.deltaspike.core.api.jmx.JmxManaged;
import org.apache.deltaspike.core.api.jmx.MBean;

@Singleton
@MBean(objectName = "io.fabric8.jube:type=About", description = "Information about Jube")
public class About implements AboutMBean {

    @JmxManaged(description = "Returns the Jube version")
    private String version;

    public About() {
        this.version = JubeVersionUtils.getReleaseVersion();
    }

    @Override
    public String getVersion() {
        return version;
    }
}
