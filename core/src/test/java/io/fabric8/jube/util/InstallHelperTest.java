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
package io.fabric8.jube.util;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InstallHelperTest {

    @Test
    public void testWriteEnvironmentVariables() throws Exception {
        // copy the file so we can override it for testing purpose
        Path src = FileSystems.getDefault().getPath("src/test/resources/env.sh");
        Path target = FileSystems.getDefault().getPath("target/env.sh");

        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);

        File env = new File("target/env.sh");

        Map<String, String> envs = new LinkedHashMap<>();
        envs.put("HTTP_PORT", "4444");
        envs.put("SSH_PORT", "5555");

        InstallHelper.writeEnvironmentVariables(env, envs);

        List<String> lines = Files.readAllLines(env.toPath());

        // HTTP_PORT should come before KARAF_OPTS
        int pos1 = 0;
        int pos2 = 0;
        int pos3 = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("export HTTP_PORT=\"4444\"")) {
                pos1 = i;
            } else if (line.contains("export SSH_PORT=\"5555\"")) {
                pos2 = i;
            } else if (line.contains("export KARAF_OPTS")) {
                pos3 = i;
            }
        }

        Assert.assertTrue(pos1 < pos2);
        Assert.assertTrue(pos1 < pos3);
        Assert.assertTrue(pos2 < pos3);
    }
}
