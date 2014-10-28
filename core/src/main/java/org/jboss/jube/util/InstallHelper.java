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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.fabric8.common.util.Closeables;
import io.fabric8.common.util.Files;
import io.fabric8.common.util.Objects;

/**
 */
public class InstallHelper {
    private static final Logger log = Logger.getLogger(InstallHelper.class.getName());

    private static final Matcher DEFAULT_MATCHER = new DefaultMatcher();

    /**
     * chmods the various scripts in the installation
     */
    public static void chmodAllScripts(File installDir) {
        if (installDir == null) {
            System.out.println("WARN: installDir is null!");
            return;
        }
        chmodScripts(installDir, DEFAULT_MATCHER);

        File binDir = new File(installDir, "bin");
        if (binDir.exists()) {
            chmodScripts(binDir, DEFAULT_MATCHER);
        }

        File executables = new File(installDir, "executables.properties");
        if (executables.exists()) {
            try {
                Properties properties = new Properties();
                try (InputStream stream = new FileInputStream(executables)) {
                    properties.load(stream);
                }
                for (String dir : properties.stringPropertyNames()) {
                    String property = properties.getProperty(dir);
                    Matcher matcher = new RegexpMatcher(property);
                    log.info(String.format("CHMOD %s with pattern %s", dir, property));
                    chmodScripts(new File(installDir, dir), matcher);
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Lets make sure all shell scripts are executable
     */
    protected static void chmodScripts(File dir, Matcher matcher) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (matcher.match(file)) {
                        //noinspection ResultOfMethodCallIgnored
                        file.setExecutable(true);
                    }
                }
            }
        }
    }

    /**
     * Appends the environment variables to the env.sh script file
     */
    public static void writeEnvironmentVariables(File envScriptFile, Map<String, String> environmentVariables) throws IOException {
        PrintStream writer = new PrintStream(new FileOutputStream(envScriptFile, true));
        try {
            writer.println();

            Set<Map.Entry<String, String>> entries = environmentVariables.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String name = entry.getKey();
                String value = entry.getValue();

                writer.println("export " + name + "=\"" + value + "\"");
            }
            writer.println();
        } finally {
            Closeables.closeQuietly(writer);
        }
    }

    private static interface Matcher {
        boolean match(File file);
    }

    private static class DefaultMatcher implements Matcher {
        public boolean match(File file) {
            String name = file.getName();
            String extension = Files.getFileExtension(name);
            return (Objects.equal(name, "launcher") || Objects.equal(extension, "sh") || Objects.equal(extension, "bat") || Objects.equal(extension, "cmd"));
        }
    }

    private static class RegexpMatcher implements Matcher {
        private final Pattern pattern;

        private RegexpMatcher(String regexp) {
            pattern = Pattern.compile(regexp);
        }

        public boolean match(File file) {
            return pattern.matcher(file.getName()).matches();
        }
    }

}
