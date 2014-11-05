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
package io.fabric8.jube.process;

import java.io.File;
import java.nio.charset.Charset;

import com.google.common.io.Files;
import io.fabric8.jube.process.service.ProcessManagerService;
import io.fabric8.jube.util.JubeVersionUtils;
import io.hawt.aether.OpenMavenURL;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class ProcessManagerServiceTest {

    private static Logger LOG = LoggerFactory.getLogger(ProcessManagerServiceTest.class);

    protected File installDir;
    protected ProcessManagerService processManagerService;
    InstallOptions installOptions;
    InstallTask postInstall;
    String firstJvmOption = "-Dfoo=bar";
    String secondJvmOption = "-server";

    @Before
    public void setUp() throws Exception {
        String basedir = System.getProperty("basedir", ".");
        installDir = new File(basedir + "/target/processes/" + getClass().getName()).getCanonicalFile();
        LOG.info("Installing processes to {}", installDir.getAbsolutePath());

        processManagerService = new ProcessManagerService(installDir);

        String version = JubeVersionUtils.getReleaseVersion();

        installOptions = new InstallOptions.InstallOptionsBuilder().
                jvmOptions(firstJvmOption, secondJvmOption).
                url(new OpenMavenURL("io.fabric8.jube.itests/cxf-cdi/" + version + "/zip/image")).build();

        LOG.info("Installation options: {}", installOptions);
    }

    @After
    public void destroy() throws Exception {
        // noop
    }

    @Test
    public void shouldGenerateJvmConfig() throws Exception {
        Installation installation = processManagerService.install(installOptions, postInstall);
        String generatedEnvSh = Files.toString(new File(installDir, "1/env.sh"), Charset.forName("UTF-8"));

        LOG.info("Found env.sh: {}", generatedEnvSh);

        ProcessController controller = installation.getController();
        assertNotNull("controller", controller);
        controller.start();

        LOG.info("Started process");

        Thread.sleep(1000);

        Long pid = controller.getPid();
        assertNotEquals("Should have a PID!", pid);

        controller.stop();

        LOG.info("Stopped process");
    }

}
