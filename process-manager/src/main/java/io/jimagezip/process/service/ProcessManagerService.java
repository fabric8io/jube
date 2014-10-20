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
package io.jimagezip.process.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.common.util.Objects;
import io.fabric8.common.util.Strings;
import io.fabric8.common.util.Zips;
import io.hawt.aether.OpenMavenURL;
import io.jimagezip.process.DownloadStrategy;
import io.jimagezip.process.InstallContext;
import io.jimagezip.process.Installation;
import io.jimagezip.process.config.ConfigHelper;
import io.jimagezip.process.config.ProcessConfig;
import io.jimagezip.process.support.command.Command;
import io.jimagezip.process.support.command.Duration;
import io.jimagezip.process.InstallOptions;
import io.jimagezip.process.InstallTask;
import io.jimagezip.process.ProcessController;
import io.jimagezip.process.support.DefaultProcessController;
import io.jimagezip.process.support.FileUtils;
import io.jimagezip.util.InstallHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.io.ByteStreams.copy;
import static io.jimagezip.process.support.ProcessUtils.findInstallDir;

public class ProcessManagerService implements ProcessManagerServiceMBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessManagerService.class);
    private static final String INSTALLED_BINARY = "install.bin";

    private Executor executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("fabric-process-manager-%s").build());
    private File storageLocation;
    private int lastId = 0;
    private final Duration untarTimeout = Duration.valueOf("1h");
    private final Duration postUnpackTimeout = Duration.valueOf("1h");
    private final Duration postInstallTimeout = Duration.valueOf("1h");
    private SortedMap<String, Installation> installations = Maps.newTreeMap();
    private final ObjectName objectName;

    private MBeanServer mbeanServer;

    public ProcessManagerService() throws MalformedObjectNameException {
        this(new File(System.getProperty("karaf.processes", System.getProperty("karaf.base") + File.separatorChar + "processes")));
    }

    public ProcessManagerService(File storageLocation) throws MalformedObjectNameException {
        this.storageLocation = storageLocation;
        this.objectName = new ObjectName("io.fabric8:type=LocalProcesses");
    }

    public void bindMBeanServer(MBeanServer mbeanServer) {
        unbindMBeanServer(this.mbeanServer);
        this.mbeanServer = mbeanServer;
        if (mbeanServer != null) {
            registerMBeanServer(mbeanServer);
        }
    }

    public void unbindMBeanServer(MBeanServer mbeanServer) {
        if (mbeanServer != null) {
            unregisterMBeanServer(mbeanServer);
            this.mbeanServer = null;
        }
    }

    public void registerMBeanServer(MBeanServer mbeanServer) {
        try {
            if (!mbeanServer.isRegistered(objectName)) {
                mbeanServer.registerMBean(this, objectName);
            }
        } catch (Exception e) {
            LOGGER.warn("An error occurred during mbean server registration: " + e, e);
        }
    }

    public void unregisterMBeanServer(MBeanServer mbeanServer) {
        if (mbeanServer != null) {
            try {
                ObjectName name = objectName;
                if (mbeanServer.isRegistered(name)) {
                    mbeanServer.unregisterMBean(name);
                }
            } catch (Exception e) {
                LOGGER.warn("An error occurred during mbean server registration: " + e, e);
            }
        }
    }

    public void init() throws Exception {
        // lets find the largest number in the current directory as we are on startup
        lastId = 0;
        File[] files = storageLocation.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String name = file.getName();
                    if (name.startsWith(".")) {
                        LOGGER.debug("Ignoring deleted installation at folder " + name);
                        continue;
                    }
                    if (name.matches("\\d+")) {
                        try {
                            int id = Integer.parseInt(name);
                            if (id > lastId) {
                                lastId = id;
                            }
                        } catch (NumberFormatException e) {
                            // should never happen :)
                        }
                    }
                    // TODO: we do not have the url this installation was created from
                    OpenMavenURL url = null;
                    ProcessConfig config = ConfigHelper.loadProcessConfig(file);
                    createInstallation(url, name, findInstallDir(file), config);
                }
            }
        }

    }


    @Override
    public String toString() {
        return "ProcessManager(" + storageLocation + ")";
    }

    @Override
    public ImmutableList<Installation> listInstallations() {
        return ImmutableList.copyOf(installations.values());
    }

    @Override
    public ImmutableMap<String, Installation> listInstallationMap() {
        return ImmutableMap.copyOf(installations);
    }

    @Override
    public Installation getInstallation(String id) {
        return installations.get(id);
    }

    @Override
    public Installation install(final InstallOptions options, final InstallTask postInstall) throws Exception {
        @SuppressWarnings("serial")
        InstallTask installTask = new InstallTask() {
            @Override
            public void install(InstallContext installContext, ProcessConfig config, String id, File installDir) throws Exception {
                config.setName(options.getName());
                File archive = getDownloadStrategy(options).downloadContent(options.getUrl(), installDir);
                if (archive == null) {
                    archive = new File(installDir, INSTALLED_BINARY);
                }
                File nestedProcessDirectory = null;
                if (archive.exists()) {
                    Zips.unzip(new FileInputStream(archive), installDir);

                    InstallHelper.chmodAllScripts(installDir);
                    nestedProcessDirectory = findInstallDir(installDir);
                    exportInstallDirEnvVar(options, nestedProcessDirectory);
                }
            }
        };
        return installViaScript(options, installTask);
    }

    protected DownloadStrategy getDownloadStrategy(InstallOptions options) {
        DownloadStrategy answer = options.getDownloadStrategy();
        if (answer == null) {
            answer = createDeafultDownloadStrategy();
        }
        return answer;
    }

    protected void exportInstallDirEnvVar(InstallOptions options, File nestedProcessDirectory) {
        options.getEnvironment().put("FABRIC8_PROCESS_INSTALL_DIR", nestedProcessDirectory.getAbsolutePath());
        substituteEnvironmentVariableExpressions(options.getEnvironment(), options.getEnvironment());
    }

    @Override
    public void uninstall(Installation installation) {
        installation.getController().uninstall();
        installations.remove(installation.getId());
    }

    @Override
    public ProcessConfig loadProcessConfig(File installDir, InstallOptions options) throws IOException {
        ProcessConfig config = loadControllerJson(installDir, options);
        Map<String, String> configEnv = config.getEnvironment();
        Map<String, String> optionsEnv = options.getEnvironment();
        if (optionsEnv != null) {
            configEnv.putAll(optionsEnv);
        }
        return config;
    }


    // Properties
    //-------------------------------------------------------------------------
    public File getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(File storageLocation) {
        this.storageLocation = storageLocation;
    }

    @Override
    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    // Implementation
    //-------------------------------------------------------------------------

    protected Installation installViaScript(InstallOptions options, InstallTask installTask) throws Exception {
        String id = createNextId(options);
        File installDir = createInstallDir(id);
        installDir.mkdirs();
        exportInstallDirEnvVar(options, installDir);

        ProcessConfig config = loadProcessConfig(installDir, options);
        InstallContext installContext = new InstallContext(installDir, false);
        installTask.install(installContext, config, id, installDir);
        ConfigHelper.saveProcessConfig(config, installDir);

        Installation installation = createInstallation(options.getUrl(), id, installDir, config);
        installation.getController().install();
        return installation;
    }

    protected DownloadStrategy createDeafultDownloadStrategy() {
        return new DownloadStrategy() {
            @Override
            public File downloadContent(final OpenMavenURL sourceUrl, final File installDir) throws IOException {
                Objects.notNull(sourceUrl, "sourceUrl");
                // copy the URL to the install dir
                File archive = new File(installDir, INSTALLED_BINARY);
                InputStream from = sourceUrl.getInputStream();
                if (from == null) {
                    throw new FileNotFoundException("Could not open URL: " + sourceUrl);
                }
                copy(from, new FileOutputStream(archive));
                return archive;
            }
        };
    }

    protected ProcessConfig loadControllerJson(File installDir, InstallOptions options) throws IOException {
        return ConfigHelper.loadProcessConfig(installDir);
    }

    /**
     * Returns the next process ID
     * @param options
     */
    protected synchronized String createNextId(InstallOptions options) {
        String id = options.getId();
        if (Strings.isNotBlank(id)) {
            return id;
        }

        // lets double check it doesn't exist already
        File dir;
        String answer = null;
        do {
            lastId++;
            answer = "" + lastId;
            dir = createInstallDir(answer);
        }
        while (dir.exists());
        return answer;
    }

    protected File createInstallDir(String id) {
        return new File(storageLocation, id);
    }


    protected Installation createInstallation(OpenMavenURL url, String id, File rootDir, ProcessConfig config) {
        // TODO we should support different kinds of controller based on the kind of installation
        // we could maybe discover a descriptor file to describe how to control the process?
        // or generate this file on installation time?

        File installDir = findInstallDir(rootDir);
        ProcessController controller = createController(id, config, rootDir, installDir);
        // TODO need to read the URL from somewhere...
        Installation installation = new Installation(url, id, installDir, controller, config);
        installations.put(id, installation);
        return installation;
    }

    protected ProcessController createController(String id, ProcessConfig config, File rootDir, File installDir) {
        return new DefaultProcessController(id, config, rootDir, installDir);
    }

    // TODO. This is been ripped from io.fabric8.container.process.JolokiaAgentHelper.substituteEnvironmentVariableExpressions()
    // requires a refactoring to not introduce circular dependencies
    public static void substituteEnvironmentVariableExpressions(Map<String, String> map, Map<String, String> environmentVariables) {
        Set<Map.Entry<String, String>> envEntries = environmentVariables.entrySet();
        for (String key : map.keySet()) {
            String text = map.get(key);
            String oldText = text;
            if (Strings.isNotBlank(text)) {
                for (Map.Entry<String, String> envEntry : envEntries) {
                    String envKey = envEntry.getKey();
                    String envValue = envEntry.getValue();
                    if (Strings.isNotBlank(envKey) && Strings.isNotBlank(envValue)) {
                        text = text.replace("${env:" + envKey + "}", envValue);
                    }
                }
                if (!Objects.equal(oldText, text)) {
                    map.put(key, text);
                }
            }
        }
    }

}
