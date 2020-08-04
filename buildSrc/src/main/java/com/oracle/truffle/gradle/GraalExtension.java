package com.oracle.truffle.gradle;

import org.gradle.api.Project;
import org.gradle.internal.extensibility.DefaultExtraPropertiesExtension;

import java.io.File;

/**
 * <p>Stores data which are used to configure Graal/Truffle plugins.</p>
 */
public class GraalExtension {

    public static final String DEFAULT_GRAAL_VERSION = "20.1.0";

    // Version string of graal that should be used.
    private String graalVersion;

    /**
     * <p>(Default: true) Bundle graal compiler and languages in distributions.</p>
     *
     * <p>You can disable this if you don't want to pre-configure graal compiler in the
     * project distributions. Consequently, Truffle will run in interpreter mode on Hotspot
     * (unless compiler is configured manually) and Truffle language dependencies won't
     * be loaded automatically on GraalVM (unless already installed via {@code gu}).</p>
     */
    public boolean bundleGraal = true;
    // Internal note: notice that __APP_HOME__ is replaced in start scripts only when this is enabled.
    // If this is set to false, do not modify startScripts!

    /**
     * <p>A directory where graal compiler jars are stored. Defaults to {@code buildDir/graalCompiler}.</p>
     */
    public File graalCompilerDir;

    /**
     * <p>Defines the name of the truffle language which is declared in this project (if any).</p>
     */
    public String truffleLanguage;

    public void setTruffleLanguage(String name) {
        this.truffleLanguage = name;
    }

    /**
     * <p>Set the version string for the Graal version which should be used by the plugins.</p>
     */
    public void setGraalVersion(String version) {
        this.graalVersion = version;
    }

    /**
     * <p>Get the Graal version which should be used by the plugins, or default if not provided.</p>
     */
    public String getGraalVersion() {
        if (this.graalVersion == null) {
            System.err.println("WARNING: Graal version not set. Defaulting to "+DEFAULT_GRAAL_VERSION+".");
            System.err.println("Set graalVersion using: graal { graalVersion = 'version_string' } in the build.gradle file.");
            return DEFAULT_GRAAL_VERSION;
        } else {
            return this.graalVersion;
        }
    }

    /**
     * <p>Set default value of graalCompilerDir based on project configuration and try to load graalVersion
     * from default values.</p>
     */
    void initDefault(Project project) {
        // Set default path to graal-compiler folder.
        if (this.graalCompilerDir == null) {
            this.graalCompilerDir = new File(project.getBuildDir(), "graalCompiler");
        }
        // Try to load graalVersion from default extra properties.
        Object ext = project.getExtensions().findByName("ext");
        if (ext instanceof DefaultExtraPropertiesExtension) {
            DefaultExtraPropertiesExtension props = (DefaultExtraPropertiesExtension) ext;
            Object versionCandidate = props.find("graalVersion");
            if (versionCandidate instanceof String) {
                this.graalVersion = (String) versionCandidate;
            }
        }
    }

    /**
     * <p>Initialize the Graal config extension (with defaults if needed) in the given project.</p>
     */
    static GraalExtension initInProject(Project project) {
        GraalExtension config;
        Object extension = project.getExtensions().findByName("graal");
        if (extension != null && !(extension instanceof GraalExtension)) {
            throw new IllegalStateException("Name clash - extension graal already exists.");
        }
        if (extension != null) {
            config = (GraalExtension) extension;
        } else {
            config = project.getExtensions().create("graal", GraalExtension.class);
            config.initDefault(project);
        }
        return config;
    }

}
