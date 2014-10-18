package io.jimagezip.maven;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class JImageZipArchiveConfigurationSource implements AssemblerConfigurationSource {
    private final MavenProject project;
    private final MavenSession mavenSession;
    private final MavenArchiveConfiguration archiveConfiguration;
    private final MavenFileFilter mavenFilter;
    private String[] descriptors;
    private String[] descriptorRefs;

    public JImageZipArchiveConfigurationSource(MavenProject project, MavenSession mavenSession, MavenArchiveConfiguration archiveConfiguration, MavenFileFilter mavenFilter, String[] descriptors, String[] descriptorRefs) {
        this.project = project;
        this.mavenSession = mavenSession;
        this.archiveConfiguration = archiveConfiguration;
        this.mavenFilter = mavenFilter;
        this.descriptors = descriptors;
        this.descriptorRefs = descriptorRefs;
    }

    public String[] getDescriptors() {
        return descriptors;
    }

    public String[] getDescriptorReferences() {
        return descriptorRefs;
    }

    // ============================================================================================

    public File getOutputDirectory() {
        return new File(project.getBasedir(), "target/jimagezip");
    }

    public File getWorkingDirectory() {
        return new File(project.getBasedir(), "target/jimagezip-work");
    }

    public File getTemporaryRootDirectory() {
        return new File(project.getBasedir(), "target/jimagezip-tmp");
    }

    public String getFinalName() {
        return project.getBuild().getFinalName();
        //return ".";
    }

    public ArtifactRepository getLocalRepository() {
        return getMavenSession().getLocalRepository();
    }

    public MavenFileFilter getMavenFileFilter() {
        return mavenFilter;
    }

    // Maybe use injection
    public List<MavenProject> getReactorProjects() {
        return project.getCollectedProjects();
    }

    // Maybe use injection
    public List<ArtifactRepository> getRemoteRepositories() {
        return project.getRemoteArtifactRepositories();
    }

    public MavenSession getMavenSession() {
        return mavenSession;
    }

    public MavenArchiveConfiguration getJarArchiveConfiguration() {
        return archiveConfiguration;
    }

    // X
    public String getEncoding() {
        return project.getProperties().getProperty("project.build.sourceEncoding");
    }

    // X
    public String getEscapeString() {
        return null;
    }

    // X
    public MavenProject getProject() {
        return project;
    }

    // X
    public File getBasedir() {
        return project.getBasedir();
    }

    // X
    public boolean isIgnoreDirFormatExtensions() {
        return true;
    }

    // X
    public boolean isDryRun() {
        return false;
    }

    // X
    public String getClassifier() {
        return null;
    }

    // X
    public List<String> getFilters() {
        return Collections.emptyList();
    }

    // X
    public File getDescriptorSourceDirectory() {
        return null;
    }

    // X
    public File getArchiveBaseDirectory() {
        return null;
    }

    // X
    public String getDescriptorId() {
        return null;
    }

    // X
    public String getDescriptor() {
        return null;
    }

    // X
    public String getTarLongFileMode() {
        return "warn";
    }

    // X
    public File getSiteDirectory() {
        return null;
    }

    // X
    public boolean isSiteIncluded() {
        return false;
    }

    // X
    public boolean isAssemblyIdAppended() {
        return false;
    }

    // X
    public boolean isIgnoreMissingDescriptor() {
        return false;
    }

    // X: (maybe inject MavenArchiveConfiguration)
    public String getArchiverConfig() {
        return null;
    }

    public boolean isUpdateOnly() {
        return false;
    }

    public boolean isUseJvmChmod() {
        return false;
    }

    public boolean isIgnorePermissions() {
        return true;
    }
}
