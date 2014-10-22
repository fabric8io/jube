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
package org.jboss.jube.util;

import io.fabric8.common.util.Files;
import io.fabric8.common.util.Objects;

import java.io.File;

/**
 */
public class InstallHelper {
    /**
     * chmods the various scripts in the installation
     */
    public static void chmodAllScripts(File installDir) {
        if (installDir == null) {
            System.out.println("WARN: installDir is null!");
            return;
        }
        chmodScripts(installDir);
        File binDir = new File(installDir, "bin");
        if (binDir.exists()) {
            chmodScripts(binDir);
        }
    }

    /**
     * Lets make sure all shell scripts are executable
     */
    protected static void chmodScripts(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    String extension = Files.getFileExtension(name);
                    if (Objects.equal(name, "launcher") || Objects.equal(extension, "sh") || Objects.equal(extension, "bat") || Objects.equal(extension, "cmd")) {
                        file.setExecutable(true);
                    }
                }
            }
        }
    }
}
