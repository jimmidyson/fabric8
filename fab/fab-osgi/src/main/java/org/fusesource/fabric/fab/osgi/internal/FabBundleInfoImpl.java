/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.fabric.fab.osgi.internal;

import aQute.lib.osgi.Analyzer;
import org.apache.felix.utils.version.VersionCleaner;
import org.fusesource.fabric.fab.DependencyTree;
import org.fusesource.fabric.fab.PomDetails;
import org.fusesource.fabric.fab.osgi.FabBundleInfo;
import org.fusesource.fabric.fab.osgi.ServiceConstants;
import org.ops4j.net.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.*;

import static org.fusesource.fabric.fab.util.Strings.notEmpty;

/**
 * Information about a resolved Fuse Application Bundle.  This class will allow you to access the FAB's InputStream
 * as well as get the list of the additional bundles and features that are required by this FAB.
 */
public class FabBundleInfoImpl implements FabBundleInfo, VersionResolver {

    private static final Logger LOG = LoggerFactory.getLogger(FabBundleInfo.class);

    private final FabClassPathResolver classPathResolver;
    private final String fabUri;
    private final Properties instructions;
    private final Configuration configuration;
    private final Map<String, Object> embeddedResources;
    private final PomDetails pomDetails;
    private final Set<String> actualImports = new HashSet<String>();

    public FabBundleInfoImpl(FabClassPathResolver classPathResolver, String fabUri, Properties instructions, Configuration configuration, Map<String, Object> embeddedResources, PomDetails pomDetails) {
        super();
        this.classPathResolver = classPathResolver;
        this.fabUri = fabUri;
        this.instructions = instructions;
        this.configuration = configuration;
        this.embeddedResources = embeddedResources;
        this.pomDetails = pomDetails;
    }

    @Override
    public InputStream getInputStream() throws Exception {
        return BndUtils.createBundle(
                URLUtils.prepareInputStream(new URL(fabUri), configuration.getCertificateCheck()),
                instructions,
                fabUri,
                OverwriteMode.MERGE,
                embeddedResources,
                classPathResolver.getExtraImportPackages(),
                actualImports,
                this);
    }

    @Override
    public Set<String> getImports() {
        return Collections.unmodifiableSet(actualImports);
    }

    @Override
    public Collection<DependencyTree> getBundles() {
        return classPathResolver.getInstallDependencies();
    }

    @Override
    public Collection<URI> getFeatureURLs() {
        return classPathResolver.getInstallFeatureURLs();
    }

    @Override
    public Collection<String> getFeatures() {
        return classPathResolver.getInstallFeatures();
    }

    @Override
    public PomDetails getPomDetails() {
        return pomDetails;
    }

    @Override
    public String resolvePackageVersion(String packageName) {
        DependencyTree dependency = resolvePackageDependency(packageName);
        if (dependency != null) {
            // lets find the export packages and use the version from that
            if (dependency.isBundle()) {
                String exportPackages = dependency.getManifestEntry("Export-Package");
                if (notEmpty(exportPackages)) {
                    Map<String, Map<String, String>> values = new Analyzer().parseHeader(exportPackages);
                    Map<String, String> map = values.get(packageName);
                    if (map != null) {
                        String version = map.get("version");
                        if (version == null) {
                            version = map.get("specification-version");
                        }
                        if (version != null) {
                            return toVersionRange(version);
                        }
                    }
                }
            }
            String version = dependency.getVersion();
            if (version != null) {
                // lets convert to OSGi
                String osgiVersion = VersionCleaner.clean(version);
                return toVersionRange(osgiVersion);
            }
        }
        return null;
    }

    @Override
    public String resolveExportPackageVersion(String packageName) {
        List<DependencyTree> dependencies = new ArrayList<DependencyTree>(classPathResolver.getSharedDependencies());

        // lets add the root too in case its an exported package we are resolving
        dependencies.add(classPathResolver.getRootTree());

        DependencyTree dependency = resolvePackageDependency(packageName, dependencies);
        if (dependency != null) {
            return Versions.getOSGiPackageVersion(dependency, packageName);

        }
        return null;
    }

    @Override
    public boolean isPackageOptional(String packageName) {
        DependencyTree dependency = resolvePackageDependency(packageName);
        if (dependency != null) {
            // mark optional dependencies which are explicitly marked as included as not being optional
            return dependency.isThisOrDescendantOptional() && classPathResolver.getOptionalDependencyFilter().matches(dependency);
        }
        return true;
    }

    public DependencyTree resolvePackageDependency(String packageName) {
        return resolvePackageDependency(packageName, classPathResolver.getSharedDependencies());
    }

    protected DependencyTree resolvePackageDependency(String packageName, List<DependencyTree> dependencies) {
        for (DependencyTree dependency : dependencies) {
            try {
                Set<String> packages = dependency.getPackages();
                if (packages.contains(packageName)) {
                    return dependency;
                }
            } catch (IOException e) {
                LOG.warn("Failed to get the packages on dependency: " + dependency + ". " + e, e);
            }
        }
        return null;
    }

    public String toVersionRange(String version) {
        int digits = ServiceConstants.DEFAULT_VERSION_DIGITS;
        String value = classPathResolver.getManifestProperty(ServiceConstants.INSTR_FAB_VERSION_RANGE_DIGITS);
        if (notEmpty(value)) {
            try {
                digits = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOG.warn("Failed to parse manifest header " + ServiceConstants.INSTR_FAB_VERSION_RANGE_DIGITS + " as a number. Got: '" + value + "' so ignoring it");
            }
            if (digits < 0 || digits > 4) {
                LOG.warn("Invalid value of manifest header " + ServiceConstants.INSTR_FAB_VERSION_RANGE_DIGITS + " as value " + digits + " is out of range so ignoring it");
                digits = ServiceConstants.DEFAULT_VERSION_DIGITS;
            }
        }
        return Versions.toVersionRange(version, digits);
    }
}
