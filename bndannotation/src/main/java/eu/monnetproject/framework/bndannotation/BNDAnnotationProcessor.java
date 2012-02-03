package eu.monnetproject.framework.bndannotation;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import aQute.lib.osgi.Analyzer;
import aQute.lib.osgi.Clazz;
import aQute.lib.osgi.FileResource;
import aQute.lib.osgi.TagResource;
import eu.monnetproject.framework.bndannotation.component.AnnotationReader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

/**
 * Goal which touches a timestamp file.
 *
 * @goal bndannotation
 * 
 * @phase process-classes
 * @requiresDependencyResolution compile
 */
public class BNDAnnotationProcessor
        extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     */
    protected MavenProject project;
    /** @component */
    private MavenProjectBuilder mavenProjectBuilder;
    /** @component */
    private org.apache.maven.artifact.factory.ArtifactFactory artifactFactory;
    /** @component */
    private org.apache.maven.artifact.resolver.ArtifactResolver resolver;
    /**@parameter default-value="${localRepository}" */
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;
    /** @parameter default-value="${project.remoteArtifactRepositories}" */
    private java.util.List remoteRepositories;
    /** @component */
    private ArtifactMetadataSource artifactMetadataSource;
    /**
     * Location of the file.
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    private File classesDirectory;
    /**
     * @parameter expression="${project.build.directory}/scr-plugin-generated"
     * @required
     * @readonly
     */
    private File outputDirectory;

    public void execute()
            throws MojoExecutionException {
        try {
            File f = classesDirectory;
            final List<File> allClasses = getAllClasses(f);
            final Analyzer analyzer = new Analyzer();
            resolveClassLoader();
            final File osgiInf = new File(outputDirectory, "OSGI-INF");

            final StringBuilder serviceCompList = new StringBuilder();

            for (File f2 : allClasses) {
                try {
                    Clazz clazz = new Clazz(f2.getPath(), new FileResource(f2));
                    final AnnotationReader annotationReader = new AnnotationReader(analyzer, clazz);
                    clazz.parseClassFileWithCollector(annotationReader);
                    if (annotationReader.getComponent().implementation != null) {
                        if(!outputDirectory.exists()) {
                            outputDirectory.mkdir();
                        }
                        if (!osgiInf.exists()) {
                            osgiInf.mkdir();
                        }
                        final PrintStream pw = new PrintStream(new File(osgiInf, annotationReader.getComponent().implementation + ".xml"));
                        final TagResource tagResource = new TagResource(annotationReader.getComponent().getTag());
                        tagResource.write(pw);
                        pw.close();
                        if (serviceCompList.length() > 0) {
                            serviceCompList.append(",");
                        }
                        serviceCompList.append("OSGI-INF/").append(annotationReader.getComponent().implementation).append(".xml");

                    }
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }
            if(outputDirectory.exists()) {
                addResources();
            }
            if (serviceCompList.length() > 0) {
                project.getProperties().setProperty("Service-Component", serviceCompList.toString());
            }
        } catch (Exception x) {
            throw new MojoExecutionException("", x);
        }
    }

    private void resolveClassLoader() throws Exception {

        final Artifact pomArtifact = artifactFactory.createArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion(), null, "pom");
        final MavenProject pomProject = mavenProjectBuilder.buildFromRepository(pomArtifact, remoteRepositories, localRepository);
        final Set resolvedArtifacts = pomProject.createArtifacts(this.artifactFactory, null, null);
        final ArtifactFilter filter = new ScopeArtifactFilter("compile");
        final ArtifactResolutionResult arr = resolver.resolveTransitively(resolvedArtifacts, pomArtifact, pomProject.getManagedVersionMap(), localRepository, remoteRepositories, artifactMetadataSource, filter);
        Set<Artifact> artifacts = arr.getArtifacts();
        Set<URL> urls = new HashSet<URL>();
        for (Artifact artifact : artifacts) {
            urls.add(artifact.getFile().toURI().toURL());
        }

        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class sysclass = URLClassLoader.class;

        try {
            Method method = sysclass.getDeclaredMethod("addURL", new Class[]{URL.class});
            method.setAccessible(true);
            for (URL url : urls) {
                method.invoke(sysloader, new Object[]{url});
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new IOException("Error, could not add URL to system classloader");
        }
    }

    private List<File> getAllClasses(File f) {
        if (f.isDirectory()) {
            LinkedList<File> files = new LinkedList<File>();
            for (File f2 : f.listFiles()) {
                files.addAll(getAllClasses(f2));
            }
            return files;
        } else if (f.getName().endsWith(".class")) {
            return Collections.singletonList(f);
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    private void addResources() {
        // now add the descriptor directory to the maven resources
        final String ourRsrcPath = this.outputDirectory.getAbsolutePath();
        boolean found = false;
        @SuppressWarnings("unchecked")
        final Iterator<Resource> rsrcIterator = this.project.getResources().iterator();
        while (!found && rsrcIterator.hasNext()) {
            final Resource rsrc = rsrcIterator.next();
            found = rsrc.getDirectory().equals(ourRsrcPath);
        }
        if (!found) {
            final Resource resource = new Resource();
            resource.setDirectory(this.outputDirectory.getAbsolutePath());
            this.project.addResource(resource);
        }
    }
}
