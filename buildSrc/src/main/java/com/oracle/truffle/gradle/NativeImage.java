package com.oracle.truffle.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class NativeImage extends DefaultTask {

    // Task inputs:
    private final DirectoryProperty outputDir = getProject().getObjects().directoryProperty();
    private final Property<String> outputName = getProject().getObjects().property(String.class);

    private final ConfigurableFileCollection classpath = getProject().files();
    // Can be either a jar file (a Jar task is also acceptable) or a main class name
    private final Property<Object> executable = getProject().getObjects().property(Object.class);
    private final ListProperty<String> cmdArgs = getProject().getObjects().listProperty(String.class);

    public NativeImage() {
        // Set default output directory and classpath
        File outputDir = new File(getProject().getBuildDir(), "nativeImage");
        this.outputDir.set(outputDir);
        this.classpath.from(getDefaultClasspath());
        this.setGroup("graal");
    }

    /**
     * Override the output directory location where the compiled binary is stored.
     *
     * By default, all compiled outputs are stored in a {@code nativeImage} directory
     * in the {@code build} folder.
     *
     * @param dir Output directory.
     */
    public void setOutputDir(Object dir) {
        this.outputDir.set(getProject().file(dir));
    }

    /**
     * Get the output directory where the compiled binaries are stored.
     *
     * @return Directory with the compiled binary.
     */
    @OutputDirectory
    public File getOutputDir() {
        /*
            Note that we can't simply return the binary file itself, because:
                a) we don't know all the rules native-image uses to make the name
                b) there might be other files as well (shared library generates .h files)

            Therefore we just mark the whole directory as output.
         */
        return this.outputDir.getAsFile().get();
    }

    /**
     * Override the name of the output binary. Default is chosen by {@code native-image}.
     *
     * @param name Name of the binary.
     */
    public void setOutputName(String name) {
        this.outputName.set(name);
    }

    /**
     * Get the intended name of the output binary - null if not set.
     * @return Name of the binary.
     */
    @Input @Optional
    public String getOutputName() {
        return this.outputName.getOrNull();
    }

    /**
     * Set the main class name of the compiled binary.
     *
     * If main class is set, you can't set a main jar file.
     *
     * @param className Name of the main class of the binary.
     */
    public void setForMainClass(String className) {
        assertExecutableNotSet(className);
        this.executable.set(className);
    }

    /**
     * Set the main jar file of the compiled binary.
     *
     * If the jar file is set, you can't set the main class name.
     *
     * @param jarFile A jar file to be compiled and executed.
     */
    public void setForJarFile(Object jarFile) {
        assertExecutableNotSet(jarFile);
        this.executable.set(getProject().file(jarFile));
    }

    /**
     * Set the main jar file of the compiled binary based on an existing jar task.
     *
     * If the jar file is set, you can't set the main class name.
     *
     * @param jarTask A task whose output is going to be used as
     */
    public void setForJarTask(Jar jarTask) {
        this.dependsOn(jarTask);
        setForJarFile(jarTask.getOutputs().getFiles().iterator().next());
    }

    /**
     * Executable is either a {@code String} (main class name) or a {@code File} (jar file)
     * that is used to generate this native image binary.
     *
     * @return Main class name or a jar file location.
     */
    @Input @Optional
    public Object getExecutable() {
        return this.executable.get();
    }

    /**
     * Set command line arguments for the native image process.
     *
     * @param args List of command line arguments.
     */
    public void cmdArgs(String... args) {
        this.cmdArgs.set(Arrays.asList(args));
    }

    /**
     * Add additional arguments for the native image process.
     *
     * (Compared to {@code setCmdArgs}, this appends the arguments but does not
     * rewrite them)
     *
     * @param args List of arguments to be appended.
     */
    public void appendCmdArgs(String... args) {
        this.cmdArgs.addAll(args);
    }

    /**
     * Extra command line arguments for the native image process.
     *
     * @return Command line arguments.
     */
    @Input
    public Iterable<String> getCmdArgs() {
        return this.cmdArgs.get();
    }

    /**
     * Append additional items to the classpath of this binary.
     *
     * @param items Extra classpath items.
     */
    public void appendClasspath(Object... items) {
        this.classpath.from(items);
    }

    /**
     * Set the classpath of this binary to the given values. Defaults to runtime classpath.
     *
     * @param items New classpath of this binary.
     */
    public void classpath(Object... items) {
        this.classpath.setFrom(items);
    }

    /**
     * Class path for the native image process.
     * @return Classpath of the compiled binary.
     */
    @Classpath
    public Iterable<File> getClasspath() {
        return this.classpath.getFiles();
    }

    @TaskAction
    public void compileNativeImage() {
        Project project = getProject();
        ensureNativeImageAvailable(project);
        ensureOutputDir();
        project.exec(exec -> {
            exec.setExecutable(getNativeImagePath());
            List<String> args = new ArrayList<>();
            // Apply class path
            args.add("-cp");
            args.add(this.classpath.getAsPath());
            // Add user defined arguments
            args.addAll(cmdArgs.get());
            // Add main class/jar
            Object executable = this.executable.get();
            if (executable instanceof String) {
                // Set main class
                args.add((String) executable);
            } else if (executable instanceof File) {
                args.add("-jar");
                args.add(((File) executable).getAbsolutePath());
            } else {
                throw new IllegalStateException("Expected File or String as executable.");
            }
            args.add("-H:Path=" + this.outputDir.get().getAsFile().getAbsolutePath());
            String name = this.outputName.getOrNull();
            if (name != null) {
                args.add("-H:Name=" + name);
            }
            System.out.println("Running native image with: "+args);
            exec.setArgs(args);
        });
    }

    /* Helper function that ensures only one executable is set for the task */
    private void assertExecutableNotSet(Object newValue) {
        if (executable.isPresent()) {
            throw new IllegalStateException(
                    "Cannot set executable to " + newValue +
                            ". Executable already set to " + executable.get()
            );
        }
    }

    /* Get default classpath for the binary based on the runtime classpath of the main sources. */
    private Iterable<File> getDefaultClasspath() {
        JavaPluginConvention javaPlugin = getProject().getConvention().findPlugin(JavaPluginConvention.class);
        if (javaPlugin == null) {
            throw new IllegalStateException("Java plugin not configured.");
        }
        SourceSet mainSources = javaPlugin.getSourceSets().findByName("main");
        if (mainSources == null) {
            // If there are no main sources, just ignore and leave it empty.
            return Collections.emptyList();
        }
        return mainSources.getRuntimeClasspath().getFiles();
    }

    /* Ensure the output directory of this task exists. */
    private void ensureOutputDir() {
        File outputDir = this.outputDir.get().getAsFile();
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new IllegalStateException(
                        "Cannot create output directory at "+outputDir.getAbsolutePath()
                );
            }
        }
        if (!outputDir.isDirectory()) {
            throw new IllegalStateException(
                    "Output "+outputDir.getAbsolutePath()+" is not a directory."
            );
        }
    }

    private static String getNativeImagePath() {
        return PluginUtils.getJavaHome() + "/bin/native-image";
    }

    private static void ensureNativeImageAvailable(Project project) {
        if (!PluginUtils.isGraalVM()) {
            throw new IllegalStateException("Not running on GraalVM. Native image not available.");
        }
        try {
            project.exec(exec -> {
                exec.setExecutable(getNativeImagePath());
                exec.setStandardOutput(new OutputStream() {
                    @Override
                    public void write(int b) {
                        // just ignore...
                    }
                });
            });
        } catch (Exception e) {
            System.err.println("Failed native image execution: "+e.getMessage());
            throw new IllegalStateException("Native image not installed. Run `gu install native-image`.");
        }
    }

}
