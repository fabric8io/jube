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
package io.jimagezip.util;

import io.fabric8.common.util.IOHelpers;
import io.fabric8.common.util.Strings;
import io.hawt.aether.OpenMavenURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parses docker image names and converts them to maven coordinates
 */
public class ImageMavenCoords {
    private static final transient Logger LOG = LoggerFactory.getLogger(ImageMavenCoords.class);

    private static String defaultVersion = findJImageZipVersion();


    private final String dockerImage;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String type;
    private final String classifier;

    /**
     * Parses the given docker image name of the form "name", or "user/name" or "registry/user/name" and return a set of maven coordinates to download the corresponding jimagezip.
     */
    public static String dockerImageToMavenCoords(String imageName) {
        return parse(imageName).getMavenCoords();
    }

    /**
     * Parses the given docker image name of the form "name", or "user/name" or "registry/user/name" and return the maven url
     */
    public static OpenMavenURL dockerImageToMavenURL(String imageName) {
        return parse(imageName).getMavenURL();
    }

    private OpenMavenURL getMavenURL() {
        return new OpenMavenURL(getMavenCoords());
    }

    /**
     * Parsers the docker image name of the form "name", or "user/name" or "registry/user/name" into its maven coordinate pieces
     */
    public static ImageMavenCoords parse(String imageName) {
        String[] split = imageName.split("/");
        String groupId = "io.jimagezip.images";
        String artifactId = null;
        String version = getDefaultVersion();
        if (split == null || split.length == 0) {
            throw new IllegalArgumentException("Invalid docker image name '" + imageName + "'");
        }
        switch (split.length) {
            case 1:
                artifactId = split[0];
                break;
            default:
                groupId += "." + split[split.length - 2];
                artifactId = split[split.length - 1];
        }
        int idx = artifactId.indexOf(':');
        if (idx > 0) {
            version = artifactId.substring(idx + 1);
            artifactId = artifactId.substring(0, idx);
        }
        String type = "zip";
        String classifier = "image";

        return new ImageMavenCoords(imageName, groupId, artifactId, version, type, classifier);
    }

    protected static String getDefaultVersion() {
        return defaultVersion;
    }

    private static String findJImageZipVersion() {
        String answer = null;
        String name = "io/jimagezip/version";
        InputStream in = ImageMavenCoords.class.getClassLoader().getResourceAsStream(name);
        if (in != null) {
            try {
                answer = IOHelpers.readFully(in).trim();
            } catch (IOException e) {
                LOG.error("Failed to load default version from " + name + ". " + e, e);
            }
        }
        if (Strings.isNullOrBlank(answer)) {
            LOG.warn("Could not load the default version!");
            answer = "2.0.0-SNAPSHOT";
        }
        return answer;
    }


    public ImageMavenCoords(String dockerImage, String groupId, String artifactId, String version, String type, String classifier) {
        this.dockerImage = dockerImage;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
        this.classifier = classifier;
    }

    @Override
    public String toString() {
        return "ImageMavenCoords{" +
                "dockerImage='" + dockerImage + '\'' +
                ", groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", type='" + type + '\'' +
                ", classifier='" + classifier + '\'' +
                '}';
    }

    public String getMavenCoords() {
        return groupId + "/" + artifactId + "/" + version+ "/" + type + "/" + classifier;
    }

    public String getAetherCoords() {
        return groupId + ":" + artifactId + ":" + type + ":" + classifier + ":" + version;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }
}
