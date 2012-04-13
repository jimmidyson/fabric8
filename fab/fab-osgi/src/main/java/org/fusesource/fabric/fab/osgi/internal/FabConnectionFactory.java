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

import org.fusesource.fabric.fab.osgi.ServiceConstants;
import org.ops4j.pax.swissbox.property.BundleContextPropertyResolver;
import org.ops4j.pax.url.commons.handler.ConnectionFactory;
import org.ops4j.util.property.DictionaryPropertyResolver;
import org.ops4j.util.property.PropertyResolver;
import org.osgi.framework.BundleContext;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import static org.fusesource.fabric.fab.osgi.util.ConfigurationAdminHelper.getProperties;

/**
 * {@link ConnectionFactory} for the "fab" protocol
 */
//TODO: remove this one?
public class FabConnectionFactory implements ConnectionFactory<Configuration> {
    
    public URLConnection createConection(BundleContext bundleContext, URL url, Configuration config) throws MalformedURLException {
        String protocol = url.getProtocol();
        if (ServiceConstants.PROTOCOL_FAB.equals(protocol)) {
            if (url.getPath() == null || url.getPath().trim().length() == 0) {
                throw new MalformedURLException("Path can not be null or empty. Syntax: fab:<fab-jar-uri>" );
            }

            URL jar = new URL(url.getPath());
            return null;
        }
        throw new MalformedURLException("Unsupported protocol: " + protocol);
    }

    /**
     * Build a new configuration object based on properties available in:
     * - the org.fusesource.fabric.fab.osgi.url ConfigurationAdmin PID
     * - the org.ops4j.pax.url.mvn ConfigurationAdmin PID
     * - the system properties
     */
    public Configuration createConfiguration(PropertyResolver propertyResolver) {
        // we are not going to use the property resolver that we're being provided with, but we will build our own instead
        BundleContext context = Activator.getInstanceBundleContext();
        PropertyResolver resolver =
                new DictionaryPropertyResolver(getProperties(context, ServiceConstants.PID),
                        new DictionaryPropertyResolver(getProperties(context, "org.ops4j.pax.url.mvn"),
                                new BundleContextPropertyResolver(context)));

        return new Configuration(resolver);
    }
}
