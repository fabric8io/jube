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
package io.jimagezip.local;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.jimagezip.process.Installation;
import io.jimagezip.process.ProcessManager;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Monitors the current local processes and updates the local model to indicate started or stopped processes
 */
@Singleton
public class ProcessMonitor {
    private static final transient Logger LOG = LoggerFactory.getLogger(AutoScaler.class);

    private final LocalNodeModel model;
    private final ProcessManager processManager;
    private final long pollTime;
    private Timer timer = new Timer();

    @Inject
    public ProcessMonitor(LocalNodeModel model,
                          ProcessManager processManager,
                          @ConfigProperty(name = "processMonitor_pollTime", defaultValue = "2000")
                          long pollTime) {
        this.model = model;
        this.processManager = processManager;
        this.pollTime = pollTime;

        System.out.println("========= Starting the process monitor with poll time: " + pollTime);

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                LOG.debug("process monitor timer");
                processMonitor();
            }
        };
        timer.schedule(timerTask, pollTime, pollTime);
    }

    protected void processMonitor() {
        ImmutableMap<String, Installation> map = processManager.listInstallationMap();
        ImmutableSet<Map.Entry<String, Installation>> entries = map.entrySet();
        for (Map.Entry<String, Installation> entry : entries) {
            String id = entry.getKey();
            Installation installation = entry.getValue();
            Long pid = null;
            try {
                pid = installation.getActivePid();
            } catch (IOException e) {
                LOG.warn("Failed to access pid for " + id + ". " + e, e);
            }
            System.out.println("Process " + id + " has pid " + pid);
        }
    }

    @PreDestroy
    public void destroy() {
        if (timer != null) {
            timer.purge();
            timer.cancel();
        }
    }

    public long getPollTime() {
        return pollTime;
    }

    public LocalNodeModel getModel() {
        return model;
    }
}
