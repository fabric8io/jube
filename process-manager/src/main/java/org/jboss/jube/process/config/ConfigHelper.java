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
package org.jboss.jube.process.config;

import org.jboss.jube.util.InstallHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Helper methods for working with the environment configuration
 */
public class ConfigHelper {
    private static final transient Logger LOG = LoggerFactory.getLogger(ConfigHelper.class);

    public static ProcessConfig loadProcessConfig(File installDir) throws IOException {
        File file = createControllerConfigFile(installDir);
        ProcessConfig answer = new ProcessConfig();
        if (!file.exists()) {
            LOG.warn("Process configuration file " + file.getPath() + " does not exist");
            return answer;
        }
        // TODO load the env vars from the env.sh file?
        return answer;
    }

    /**
     * Writes the environment variables to the env.sh
     */
    public static void saveProcessConfig(ProcessConfig config, File installDir) throws IOException {
        File file = createControllerConfigFile(installDir);
        InstallHelper.writeEnvironmentVariables(file, config.getEnvironment());
    }

    public static File createControllerConfigFile(File installDir) {
        return new File(installDir, "env.sh");
    }

}
