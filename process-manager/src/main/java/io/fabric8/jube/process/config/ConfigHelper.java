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
package io.fabric8.jube.process.config;

import java.io.File;
import java.io.IOException;

import io.fabric8.jube.util.InstallHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper methods for working with the environment configuration
 */
public final class ConfigHelper {
    private static final transient Logger LOG = LoggerFactory.getLogger(ConfigHelper.class);

    private ConfigHelper() {
        // utility class
    }

    public static ProcessConfig loadProcessConfig(File installDir, boolean windows) throws IOException {
        File file = createControllerConfigFile(installDir, windows ? "env.bat" : "env.sh");
        ProcessConfig answer = new ProcessConfig(installDir);
        if (!file.exists()) {
            LOG.warn("Process configuration file " + file.getPath() + " does not exist");
            return answer;
        }
        return answer;
    }

    /**
     * Writes the environment variables to the env.sh/env.bat
     */
    public static void saveProcessConfig(ProcessConfig config, File installDir) throws IOException {
        // make sure to save for both kind of platforms
        File file = createControllerConfigFile(installDir, InstallHelper.ENVIRONMENT_VARIABLE_SCRIPT);
        InstallHelper.writeEnvironmentVariables(file, config.getEnvironment());
        file = createControllerConfigFile(installDir, InstallHelper.ENVIRONMENT_VARIABLE_SCRIPT_WINDOWS);
        InstallHelper.writeEnvironmentVariables(file, config.getEnvironment());
    }

    public static File createControllerConfigFile(File installDir, String name) {
        if (name == null) {
            name = "env.sh";
        }
        return new File(installDir, name);
    }

}
