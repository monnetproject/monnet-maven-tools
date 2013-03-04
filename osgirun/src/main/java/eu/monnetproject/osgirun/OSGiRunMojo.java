/**********************************************************************************
 * Copyright (c) 2011, Monnet Project
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Monnet Project nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE MONNET PROJECT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *********************************************************************************/
package eu.monnetproject.osgirun;

import aQute.lib.io.IO;
import aQute.lib.osgi.Analyzer;
import aQute.lib.osgi.Builder;
import aQute.lib.osgi.Jar;
import aQute.lib.osgi.Verifier;
import java.io.BufferedReader;
import java.io.File;
import java.util.Set;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.osgi.framework.launch.FrameworkFactory;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;

/**
 * Goal which touches a timestamp file.
 *
 * @goal run
 */
public class OSGiRunMojo
        extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter expression="${project}" @required
     */
    protected MavenProject project;
    /**
     * @component
     */
    private MavenProjectBuilder mavenProjectBuilder;
    /**
     * @component
     */
    private org.apache.maven.artifact.factory.ArtifactFactory artifactFactory;
    /**
     * @component
     */
    private org.apache.maven.artifact.resolver.ArtifactResolver resolver;
    /**
     * @parameter default-value="${localRepository}"
     */
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;
    /**
     * @parameter default-value="${project.remoteArtifactRepositories}"
     */
    private java.util.List remoteRepositories;
    /**
     * @component
     */
    private ArtifactMetadataSource artifactMetadataSource;
    /**
     * @parameter expression="${osgirun.fwGroupId}"
     * default-value="org.apache.felix"
     */
    private String fwGroupId;
    /**
     * @parameter expression="${osgirun.fwArtifactId}"
     * default-value="org.apache.felix.framework"
     */
    private String fwArtifactId;
    /**
     * @parameter expression="${osgirun.fwVersion}" default-value="4.0.2"
     */
    private String fwVersion;
    /**
     * @parameter
     */
    private String[] osgiProps;
    /**
     * @parameter expression="${osgirun.felixBundles}" default-value=true
     */
    private boolean felixBundles;
    /**
     * @parameter expression="${osgirun.excludeBundles}" default-value=null
     */
    private String excludedBundles;
    /**
     * @parameter expression="${integration.test}" default-value=false
     */
    private boolean integrationTest;
    private final List<String> excludedBundleNames = new ArrayList<String>(Arrays.asList(new String[]{
                "org.osgi.foundation",
                "org.apache.felix.framework",
                "biz.aQute.bnd",
                "org.osgi.core"
            }));

    public void execute()
            throws MojoExecutionException {
        init();
        try {
            final Artifact pomArtifact = artifactFactory.createArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion(), null, "pom");
            final MavenProject pomProject = mavenProjectBuilder.buildFromRepository(pomArtifact, remoteRepositories, localRepository);
            final Set resolvedArtifacts = pomProject.createArtifacts(this.artifactFactory, null, null);
            final ArtifactFilter filter = new OrArtifactFilter(new ScopeArtifactFilter("compile"), new ScopeArtifactFilter("runtime"));
            final ArtifactResolutionResult arr = resolver.resolveTransitively(resolvedArtifacts, pomArtifact, pomProject.getManagedVersionMap(), localRepository, remoteRepositories, artifactMetadataSource, filter);
            Set<Artifact> artifacts = arr.getArtifacts();
            Set<URL> urls = new HashSet<URL>();
            for (Artifact artifact : artifacts) {
                if (!excludedBundleNames.contains(artifact.getArtifactId())) {
                    urls.add(makeOSGi(artifact.getFile()));
                }
            }
            final Artifact fwArtifact = artifactFactory.createArtifact(fwGroupId, fwArtifactId, fwVersion, null, "jar");
            resolver.resolve(fwArtifact, remoteRepositories, localRepository);
            final File artifactLocal = new File(pomProject.getBuild().getDirectory() + System.getProperty("file.separator") + pomProject.getBuild().getFinalName() + ".jar");
            if (artifactLocal.exists()) {
                urls.add(artifactLocal.toURI().toURL());
            } else {
                if (pomProject.getBuild().getDirectory().startsWith("http")) {
                    final File artifactLocal2 = new File(System.getProperty("user.dir") + System.getProperty("file.separator") + "target" + System.getProperty("file.separator") + pomProject.getBuild().getFinalName() + ".jar");
                    if (artifactLocal2.exists()) {
                        urls.add(artifactLocal2.toURI().toURL());
                    } else {
                        resolveMainArtifactUsingMaven(artifactLocal, urls);
                    }
                } else {
                    resolveMainArtifactUsingMaven(artifactLocal, urls);
                }
            }
            if (felixBundles) {
                addFelixBundles(urls);
            }
            final LinkedList<URL> urlList = new LinkedList<URL>(urls);
            Collections.sort(urlList, new Comparator<URL>() {

                public int compare(URL o1, URL o2) {
                    return o1.toString().compareTo(o2.toString());
                }
            });
            runOSGi(fwArtifact.getFile().toURI().toURL(), urlList);
        } catch (Exception x) {
            getLog().error("Could not run OSGi: " + x.getClass().getName() + " " + x.getMessage());
            throw new MojoExecutionException("", x);
        }
    }

    private void resolveMainArtifactUsingMaven(final File artifactLocal, Set<URL> urls) throws MalformedURLException, ArtifactNotFoundException, ArtifactResolutionException {
        getLog().warn("Could not locate the main artifact at " + artifactLocal.getPath() + " trying to use Maven resolver... this may not work");
        resolver.resolve(project.getArtifact(), remoteRepositories, localRepository);
        urls.add(project.getArtifact().getFile().toURI().toURL());
    }

    private URL makeOSGi(File bundle) throws Exception {
        final String bsn = new JarFile(bundle).getManifest().getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);
        if (bsn != null) {
            return bundle.toURI().toURL();
        } else {
            final File barFile = new File(bundle.getPath().substring(0, bundle.getPath().length() - 3) + "bar");
            if (barFile.exists() && barFile.lastModified() >= bundle.lastModified()) {
                return barFile.toURI().toURL();
            } else {
                getLog().info(bundle.getName() + " is not an OSGi bundle... using BND to wrap it");
                final Analyzer analyzer = new Analyzer();
                try {
                    analyzer.setJar(bundle);
                    final Jar dot = analyzer.getJar();
                    if (analyzer.getProperty(Analyzer.IMPORT_PACKAGE) == null) {
                        analyzer.setProperty(Analyzer.IMPORT_PACKAGE, "*;resolution:=optional");
                    }

                    if (analyzer.getProperty(Analyzer.BUNDLE_SYMBOLICNAME) == null) {
                        Pattern p = Pattern.compile("(" + Verifier.SYMBOLICNAME.pattern()
                                + ")(-[0-9])?.*\\.jar");
                        String base = bundle.getName();
                        Matcher m = p.matcher(base);
                        base = "Untitled";
                        if (m.matches()) {
                            base = m.group(1);
                        } else {
                            throw new RuntimeException("Can not calculate name of output bundle");
                        }
                        analyzer.setProperty(Analyzer.BUNDLE_SYMBOLICNAME, base);
                    }

                    if (analyzer.getProperty(Analyzer.EXPORT_PACKAGE) == null) {
                        String export = analyzer.calculateExportsFromContents(dot);
                        analyzer.setProperty(Analyzer.EXPORT_PACKAGE, export);
                    }

                    analyzer.mergeManifest(dot.getManifest());

                    //
                    // Cleanup the version ..
                    //
                    String version = analyzer.getProperty(Analyzer.BUNDLE_VERSION);
                    if (version != null) {
                        version = Builder.cleanupVersion(version);
                        analyzer.setProperty(Analyzer.BUNDLE_VERSION, version);
                    }


                    analyzer.calcManifest();
                    Jar jar = analyzer.getJar();
                    File f = File.createTempFile("tmpbnd", ".jar");
                    f.deleteOnExit();
                    try {
                        jar.write(f);
                        jar.close();
                        if (!f.renameTo(barFile)) {
                            IO.copy(f, barFile);
                        }
                    } finally {
                        f.delete();
                    }
                    return barFile.toURI().toURL();
                } finally {
                    analyzer.close();
                }
            }
        }
    }

    private void runOSGi(URL fwURL, Collection<URL> bundles) throws Exception {

        Framework frameWork = getFrameworkFactory(fwURL).newFramework(props());
        frameWork.init();
        frameWork.start();
        BundleContext context = frameWork.getBundleContext();
        List<Bundle> bundleRefs = new LinkedList<Bundle>();
        for (URL bundleFile : bundles) {
            try {
                getLog().info("Installing: " + bundleFile.toString());
                bundleRefs.add(context.installBundle(bundleFile.toString()));
            } catch (Exception x) {
                getLog().error("Could not install bundle " + bundleFile + " as " + x.getMessage());
                throw x;
            }
        }
        for (Bundle bundle : bundleRefs) {
            try {
                getLog().info("Starting: " + bundle.getSymbolicName());
                bundle.start();
            } catch (Exception x) {
                if (!x.getMessage().equals("Fragment bundles can not be started.")) {
                }
                getLog().error("Could not start bundle " + bundle.getSymbolicName() + " as " + x.getMessage());
                x.printStackTrace();
            }
        }
        frameWork.waitForStop(0);

        File cache = new File("felix-cache");
        if (cache.exists() && cache.isDirectory()) {
            deleteRecursively(cache);
        }
    }

    private void deleteRecursively(File cache) {
        for (File f : cache.listFiles()) {
            if (f.isDirectory()) {
                deleteRecursively(f);
            } else {
                f.delete();
            }
        }
        cache.delete();
    }

    private Map<String, String> props() throws MojoExecutionException {
        if (osgiProps != null) {
            final HashMap<String, String> map = new HashMap<String, String>();
            for (String osgiProp : osgiProps) {
                if (osgiProp == null || osgiProp.equals("null")) {
                    continue;
                }
                final String[] kv = osgiProp.split("=");
                if (kv.length != 2) {
                    throw new MojoExecutionException("Bad OSGi property: " + osgiProp);
                } else {
                    map.put(kv[0], kv[1]);
                }
            }
            return map;
        } else {
            return Collections.EMPTY_MAP;
        }
    }

    private static FrameworkFactory getFrameworkFactory(URL fwURL) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        ClassLoader loader = new URLClassLoader(
                new URL[]{fwURL}, OSGiRunMojo.class.getClassLoader());

        URL url = loader.getResource(
                "META-INF/services/org.osgi.framework.launch.FrameworkFactory");

        if (url
                != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            try {
                for (String s = br.readLine(); s != null; s = br.readLine()) {
                    s = s.trim();
                    // Try to load first non-empty, non-commented line.
                    if ((s.length() > 0) && (s.charAt(0) != '#')) {
                        return (FrameworkFactory) Class.forName(s, true, loader).newInstance();
                    }
                }
            } finally {
                if (br != null) {
                    br.close();
                }
            }
            throw new FileNotFoundException("Could not extract class name from framework launcher file");
        } else {
            throw new FileNotFoundException(fwURL + " specified as framework bundle but does not contain a framework launcher");
        }
    }

    private void addFelixBundles(Set<URL> urls) throws Exception {
        final Artifact scrArtifact = artifactFactory.createArtifact("org.apache.felix", "org.apache.felix.scr", "1.6.0", null, "jar");
        resolver.resolve(scrArtifact, remoteRepositories, localRepository);
        urls.add(scrArtifact.getFile().toURI().toURL());
        final Artifact consoleArtifact = artifactFactory.createArtifact("org.apache.felix", "org.apache.felix.shell", "1.4.1", null, "jar");
        resolver.resolve(consoleArtifact, remoteRepositories, localRepository);
        urls.add(consoleArtifact.getFile().toURI().toURL());
        final Artifact consoleTUIArtifact = artifactFactory.createArtifact("org.apache.felix", "org.apache.felix.shell.tui", "1.4.1", null, "jar");
        resolver.resolve(consoleTUIArtifact, remoteRepositories, localRepository);
        urls.add(consoleTUIArtifact.getFile().toURI().toURL());
    }

    private void init() {
        if (excludedBundles != null) {
            for (String bName : excludedBundles.split(",")) {
                excludedBundleNames.add(bName.trim());
            }
        }
        if (integrationTest) {
            getLog().info("Setting integration testing to true");
            System.setProperty("eu.monnetproject.framework.test", "true");
        }
    }
}
