/**
 *  Copyright 2005-2015 Red Hat, Inc.
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

import io.hawt.aether.OpenMavenURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses docker image names and converts them to maven coordinates
 */
public class ImageMavenCoords {
    private static final transient Logger LOG = LoggerFactory.getLogger(ImageMavenCoords.class);

    public static final String DEFAULT_GROUP_ID = "io.fabric8.jube.images";

    private final String dockerImage;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String type;
    private final String classifier;
    private String scope = "runtime";

    public ImageMavenCoords(String dockerImage, String groupId, String artifactId, String version, String type, String classifier) {
        this.dockerImage = dockerImage;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
        this.classifier = classifier;
    }

    /**
     * Parses the given docker image name of the form "name", or "user/name" or "registry/user/name" and return a set of maven coordinates to download the corresponding jube.
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

    /**
     * Parses the given docker image name of the form "name", or "user/name" or "registry/user/name" and return the maven url
     */
    public static OpenMavenURL dockerImageToMavenURL(String imageName, boolean useDefaultPrefix) {
        return parse(imageName, useDefaultPrefix).getMavenURL();
    }

    private OpenMavenURL getMavenURL() {
        return new OpenMavenURL(getMavenCoords());
    }

    /**
     * Parsers the docker image name of the form "name", or "user/name" or "registry/user/name" into its maven coordinate pieces
     */
    public static ImageMavenCoords parse(String imageName) {
        return parse(imageName, false);
    }

    /**
     * Parsers the docker image name of the form "name", or "user/name" or "registry/user/name" into its maven coordinate pieces
     */
    public static ImageMavenCoords parse(String imageName, boolean useDefaultPrefix) {
        String[] split = imageName.split("/");
        String groupId = DEFAULT_GROUP_ID;
        String artifactId;
        String version = JubeVersionUtils.getReleaseVersion();

        if (split.length == 0) {
            throw new IllegalArgumentException("Invalid docker image name '" + imageName + "'");
        }
        switch (split.length) {
        case 1:
            artifactId = split[0];
            break;
        default:
            groupId = useDefaultPrefix ? groupId + "." + split[split.length - 2] : split[split.length - 2];
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

    @Override
    public String toString() {
        return "ImageMavenCoords{"
                + "dockerImage='" + dockerImage + '\''
                + ", groupId='" + groupId + '\''
                + ", artifactId='" + artifactId + '\''
                + ", version='" + version + '\''
                + ", type='" + type + '\''
                + ", classifier='" + classifier + '\''
                + '}';
    }

    public String getMavenCoords() {
        return groupId + "/" + artifactId + "/" + version + "/" + type + "/" + classifier;
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

    public String getScope() {
        return scope;
    }
}
