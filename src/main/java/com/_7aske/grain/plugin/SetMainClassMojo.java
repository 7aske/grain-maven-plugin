package com._7aske.grain.plugin;

import com._7aske.grain.plugin.util.GrainClassFinder;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassLoaderRepository;
import org.apache.bcel.util.Repository;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

@Mojo(
        name = "set-main-class",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
        aggregator = true
)
public class SetMainClassMojo extends AbstractMojo {

    private static final String APPLICATION_ANNOTATION = "Lcom/_7aske/grain/core/configuration/GrainApplication;";

    private static final String GRAIN_PACKAGE = "com._7aske.grain";

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Parameter(readonly = true, defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (project.getGroupId().equals(GRAIN_PACKAGE)) {
            return;
        }

        GrainClassFinder classFinder = new GrainClassFinder(project, getLog());
        Set<String> classNames = classFinder.findClasses(c -> !c.startsWith(GRAIN_PACKAGE + "."));
        getLog().debug("Classes found: " + classNames);

        try {
            project.getCompileClasspathElements().add(project.getBuild().getOutputDirectory());
            project.getCompileClasspathElements().add(repoSession.getLocalRepository().toString());


            URL[] cp = project.getCompileClasspathElements().stream()
                    .map(File::new)
                    .map(File::toURI)
                    .map(uri -> {
                        try {
                            return uri.toURL();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(URL[]::new);

            for (URL url : cp) {
                getLog().debug("Classpath element: " + url);
            }

            Set<JavaClass> loadedClasses = new HashSet<>();
            try (URLClassLoader classLoader = new URLClassLoader(cp, this.getClass().getClassLoader())) {
                ClassLoaderRepository repository = new ClassLoaderRepository(classLoader);

                for (String className : classNames) {
                    loadedClasses.add(loadClass(className, repository));
                }
            }

            getLog().debug("Classes loaded: " + loadedClasses.size());

            JavaClass mainClass = findGrainApplication(loadedClasses)
                    .orElseThrow(() -> new MojoExecutionException("No GrainApplication class found"));

            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass.getClassName());

            Files.createDirectories(new File(project.getBuild().getOutputDirectory() + "/META-INF").toPath());
            try (FileOutputStream os = new FileOutputStream(project.getBuild().getOutputDirectory() + "/META-INF/MANIFEST.MF")) {
                manifest.write(os);
            }


        } catch (IOException | DependencyResolutionRequiredException e) {
            throw new MojoExecutionException(e);
        }
    }

    private JavaClass loadClass(String className, Repository repository) throws MojoExecutionException {
        try {
            return repository.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new MojoExecutionException(e);
        }
    }

    private Optional<JavaClass> findGrainApplication(Set<JavaClass> classes) {
        return classes.stream()
                .filter(c -> Arrays.stream(c.getAnnotationEntries())
                        .anyMatch(a -> a.getAnnotationType().equals(APPLICATION_ANNOTATION)))
                .findFirst();
    }
}
