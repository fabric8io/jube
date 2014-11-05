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

import java.io.IOException;
import java.io.InputStream;

import io.fabric8.utils.IOHelpers;

public final class JubeVersionUtils {

    private static String version;

    private JubeVersionUtils() {
    }

    /**
     * Gets the Jube release version such as <tt>2.0.0</tt>
     */
    public static synchronized String getReleaseVersion() {
        if (version != null) {
            return version;
        }

        String name = "io/fabric8/jube/version";
        InputStream in = JubeVersionUtils.class.getClassLoader().getResourceAsStream(name);
        if (in != null) {
            try {
                version = IOHelpers.readFully(in).trim();
            } catch (IOException e) {
                // ignore
            }
        }

        if (version == null) {
            Package aPackage = JubeVersionUtils.class.getPackage();
            if (aPackage != null) {
                version = aPackage.getImplementationVersion();
                if (version == null) {
                    version = aPackage.getSpecificationVersion();
                }
            }
        }

        if (version == null) {
            // we could not compute the version so use a blank
            version = "";
        }

        return version;
    }
}
