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
package io.jimagezip.maven;

import com.google.common.io.Closeables;
import com.google.common.io.Files;
import io.fabric8.common.util.Objects;
import io.fabric8.common.util.Zips;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugin.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugin.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugin.assembly.io.AssemblyReadException;
import org.apache.maven.plugin.assembly.io.AssemblyReader;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.fabric8.common.util.PropertiesHelper.findPropertiesWithPrefix;

/**
 * Builds a JImageZip from a known base image.
 */
@Mojo(name = "build")
public class BuildMojo extends AbstractMojo {
    private static final transient Logger LOG = LoggerFactory.getLogger(BuildMojo.class);
    public static final String DOCKER_IMAGE_PROPERTY = "docker.dataImage";
    public static final String DOCKER_BASE_IMAGE_PROPERTY = "docker.baseImage";

    @Parameter(property = "docker.dataImage", defaultValue = "${project.groupId}/${project.artifactId}")
    private String image;

    @Parameter(property = "docker.baseImage", defaultValue = "fabric8/java")
    private String baseImage;

    @Parameter
    private Map<String, String> environmentVariables;
    /**
     * A descriptor to use for building the data assembly to be exported
     * in an Docker image
     */
    @Parameter
    protected String assemblyDescriptor;

    /**
     * Reference to an assembly descriptor included.
     */
    @Parameter(property = "fabric8.assemblyDescriptorRef", defaultValue = "artifact-with-dependencies")
    protected String assemblyDescriptorRef;

    /**
     * Name of the generated image zip file
     */
    @Parameter(property = "jimagezip.outFile", defaultValue = "${project.build.directory}/${project.artifactId}-${project.version}-image.zip")
    private File outputZipFile;

    /**
     * The artifact type for attaching the generated image zip file to the project
     */
    @Parameter(property = "jimagezip.zip.artifactType", defaultValue = "zip")
    private String artifactType = "zip";

    /**
     * The artifact classifier for attaching the generated image zip file to the project
     */
    @Parameter(property = "jimagezip.zip.artifactClassifier", defaultValue = "image")
    private String artifactClassifier = "image";

    /**
     * Reference to an assembly descriptor included.
     */
    @Parameter(property = "fabric8.container.name", defaultValue = "${project.artifactId}")
    protected String service;

    /**
     * Reference to an assembly descriptor included.
     */
    @Parameter(property = "fabric8.app.name", defaultValue = "${project.name}")
    protected String serviceName;


    // ==============================================================================================================
    // Parameters required from Maven when building an assembly.
    // See also here: http://maven.40175.n5.nabble.com/Mojo-Java-1-5-Component-MavenProject-returns-null-vs-JavaDoc-parameter-expression-quot-project-quot-s-td5733805.html
    @Parameter
    private MavenArchiveConfiguration archive;

    @Component
    private MavenSession session;

    @Component
    private MavenFileFilter mavenFileFilter;

    @Component
    protected MavenProject project;

    @Component
    private AssemblyArchiver assemblyArchiver;

    @Component
    private AssemblyReader assemblyReader;

    @Component
    private ArchiverManager archiverManager;

    @Parameter(property = "project.remoteArtifactRepositories")
    protected List<RemoteRepository> remoteRepositories;

    @Component
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}")
    protected RepositorySystemSession repoSession;

    @Component
    private MavenProjectHelper projectHelper;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Generating JImageZip image " + image + " from base image " + baseImage);


        getLog().info("Assembly reference: " + assemblyDescriptorRef);

        if (assemblyDescriptor != null && assemblyDescriptorRef != null) {
            throw new MojoExecutionException("No assemblyDescriptor or assemblyDescriptorRef has been given");
        }

        System.out.println("Env vars: " + getEnvironmentVariables());

        createAssembly();
    }

    public Map<String, String> getEnvironmentVariables() {
        if (environmentVariables == null) {
            environmentVariables = new HashMap<>();
        }
        if (environmentVariables.isEmpty()) {
            environmentVariables = findPropertiesWithPrefix(project.getProperties(), "docker.env.");
        }
        if (!environmentVariables.containsKey("SERVICE")) {
            environmentVariables.put("SERVICE", service);
        }
        if (!environmentVariables.containsKey("SERVICE_NAME")) {
            environmentVariables.put("SERVICE_NAME", serviceName);
        }
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    protected void createAssembly() throws MojoFailureException, MojoExecutionException {
        String extractBaseImageRef = "extractBaseImage";
        String createZipRef = "createZip";
        //String extractBaseImageRef = "internal/extractBaseImage.xml";
        AssemblerConfigurationSource extractConfig = createAssemblyConfigurationSource(null, extractBaseImageRef);
        AssemblerConfigurationSource projectConfig = createAssemblyConfigurationSource(assemblyDescriptor, assemblyDescriptorRef);
        AssemblerConfigurationSource zipConfig = createAssemblyConfigurationSource(null, createZipRef);

        Assembly assembly = null;

        File buildDir = new File(project.getBasedir(), "target/jimagezip");
        try {
            buildDir.mkdirs();
            unpackBaseImage(buildDir);
            writeEnvironmentVariables(buildDir);

            assembly = extractAssembly(projectConfig);
            assembly.setId("docker");
            assemblyArchiver.createArchive(assembly, "maven", "dir", projectConfig, false);

            chmodScripts(buildDir);
            File binDir = new File(buildDir, "bin");
            if (binDir.exists()) {
                chmodScripts(binDir);
            }

            Zips.createZipFile(LOG, buildDir, outputZipFile);
            getLog().info("Created image zip: " + outputZipFile);

            projectHelper.attachArtifact(project, artifactType, artifactClassifier, outputZipFile);

        } catch (InvalidAssemblerConfigurationException e) {
            throw new MojoFailureException(assembly, "Assembly is incorrectly configured: " + assembly.getId(),
                    "Assembly: " + assembly.getId() + " is not configured correctly: "
                            + e.getMessage());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create assembly for image: " + e.getMessage(), e);
        }
    }

    protected void writeEnvironmentVariables(File buildDir) throws IOException {
        File envScript = new File(buildDir, "env.sh");
        PrintStream writer = new PrintStream(new FileOutputStream(envScript, true));
        try {
            writer.println();

            Map<String, String> envMap = getEnvironmentVariables();
            Set<Map.Entry<String, String>> entries = envMap.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String name = entry.getKey();
                String value = entry.getValue();

                writer.println("export " + name + "=\"" + value + "\"");
            }
            writer.println();
        } finally {
            Closeables.close(writer, false);
        }
    }


    /**
     * Lets make sure all shell scripts are executable
     */
    protected void chmodScripts(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    String extension = Files.getFileExtension(name);
                    if (name.equals("launcher") || extension.equals("sh") || extension.equals("bat") || extension.equals("cmd")) {
                        file.setExecutable(true);
                    }
                }
            }
        }
    }

    protected void unpackBaseImage(File buildDir) throws Exception {
        String imageName = project.getProperties().getProperty(DOCKER_BASE_IMAGE_PROPERTY);
        Objects.notNull(imageName, DOCKER_BASE_IMAGE_PROPERTY);

        String[] split = imageName.split("/");
        String groupId = "io.jimagezip.images";
        String artifactId = null;
        String version = project.getVersion();
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

        String coords = groupId + ":" + artifactId + ":" + type + ":" + classifier + ":" + version;
        getLog().info("Looking up JImageZip: " + coords);
        Artifact artifact = new DefaultArtifact(coords);

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(artifact);

        List<RemoteRepository> filteredRepos = new ArrayList<>();
        if (remoteRepositories != null) {
            for (Object remoteRepository : remoteRepositories) {
                if (remoteRepository instanceof RemoteRepository) {
                    filteredRepos.add((RemoteRepository) remoteRepository);
                } else {
                    getLog().warn("Invalid Remote Repository " + remoteRepository + " of type: " + remoteRepository.getClass());
                }
            }
        }
        request.setRepositories(filteredRepos);

        ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);

        Artifact foundArtifact = result.getArtifact();
        getLog().info("Found Artifact: " + foundArtifact);
        if (foundArtifact != null) {
            File file = foundArtifact.getFile();
            getLog().info("File: " + file);

            if (!file.exists() || file.isDirectory()) {
                throw new MojoExecutionException("Resolved file for " + coords + " is not a valid file: " + file.getAbsolutePath());
            }
            getLog().info("Unpacking base image " + file.getAbsolutePath() + " to build dir: " + buildDir);
            Zips.unzip(new FileInputStream(file), buildDir);
        }
    }


    private AssemblerConfigurationSource createAssemblyConfigurationSource(String descriptor, String descriptorRef) {
        String[] descriptors = descriptor != null ? new String[]{descriptor} : null;

        String[] descriptorRefs = descriptorRef != null ?
                new String[]{descriptorRef} : null;

        return new JImageZipArchiveConfigurationSource(project, session, archive, mavenFileFilter, descriptors, descriptorRefs);
    }

    private Assembly extractAssembly(AssemblerConfigurationSource config) throws MojoExecutionException, MojoFailureException {
        try {
            List<Assembly> assemblies = assemblyReader.readAssemblies(config);
            if (assemblies.size() != 1) {
                throw new MojoFailureException("Only one assembly can be used for creating a Docker base image (and not " + assemblies.size() + ")");
            }
            return assemblies.get(0);
        } catch (AssemblyReadException e) {
            throw new MojoExecutionException("Error reading assembly: " + e.getMessage(), e);
        } catch (InvalidAssemblerConfigurationException e) {
            throw new MojoFailureException(assemblyReader, e.getMessage(), "Docker assembly configuration is invalid: " + e.getMessage());
        }
    }
}
