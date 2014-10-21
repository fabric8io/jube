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
package io.jimagezip.process;

import io.jimagezip.process.service.ProcessManagerService;
import org.apache.deltaspike.core.api.config.ConfigProperty;

import javax.inject.Singleton;
import javax.management.MalformedObjectNameException;
import javax.ws.rs.Produces;
import java.io.File;

/**
 * CDI producers
 */
public class ProcessManagers {

    @Singleton
    @Produces
    public ProcessManager createProcessManager(@ConfigProperty(name = "process_dir", defaultValue = "./process") File processDir) throws MalformedObjectNameException {
        return new ProcessManagerService(processDir);
    }
}
