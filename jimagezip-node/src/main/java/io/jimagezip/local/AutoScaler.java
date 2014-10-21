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

import org.apache.deltaspike.core.api.config.ConfigProperty;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Monitors the status of the current replication controllers and pods and chooses to start new pods if there are not enough replicas
 */
@Singleton
public class AutoScaler {
    private final LocalNodeModel model;
    private final long pollTime;

    @Inject
    public AutoScaler(LocalNodeModel model,
                      @ConfigProperty(name = "autoScaler_pollTime", defaultValue = "2000")
                      long pollTime) {
        this.model = model;
        this.pollTime = pollTime;

        System.out.println("========= Starting the auto scaler with poll time: " + pollTime);
    }

    public long getPollTime() {
        return pollTime;
    }

    public LocalNodeModel getModel() {
        return model;
    }
}
