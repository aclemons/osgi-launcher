/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package nz.caffe.osgi.launcher.web;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import javax.servlet.ServletContext;

import nz.caffe.osgi.launcher.LoadCallback;
import nz.caffe.osgi.launcher.impl.BaseLauncher;

/**
 * <p>
 * This class provides the default way to instantiate and execute an osgi
 * runtime when it is embedded inside a WAR file.
 * </p>
 **/
public final class WarLauncher extends BaseLauncher {

    /**
     * The default name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_VALUE = "/WEB-INF/osgi/bundle";

    private final ServletContext servletContext;

    /**
     * @param bundleDir
     * @param cacheDir
     * @param loadCallback
     * @param servletContext 
     */
    public WarLauncher(final String bundleDir, final String cacheDir, final LoadCallback loadCallback,
            final ServletContext servletContext) {
        super(bundleDir, cacheDir, loadCallback);
        this.servletContext = servletContext;
    }

    @Override
    protected String getDefaultAutoDeployDirectory() {
        return AUTO_DEPLOY_DIR_VALUE;
    }

    @Override
    protected Properties loadPropertiesFile(final String systemPropertyName, final String defaultFileName,
            final String type) {

        // The properties file is either specified by a system
        // property or in /WEB-INF/osgi/conf
        // Try to load it from one of these places.

        // Read the properties file.
        final Properties props = new Properties();

        // See if the property URL was specified as a property.
        final String custom = System.getProperty(systemPropertyName);

        if (custom == null) {
            final InputStream is = this.servletContext.getResourceAsStream("/WEB-INF/osgi/conf/" + defaultFileName);

            if (is == null) {
                this.logger.debug("No {} found.", defaultFileName);
                return null;
            }

            // if the file exists but loading fails, fail starting the framework
            try {
                props.load(is);
            } catch (final Exception ex) {
                throw new IllegalArgumentException(
                        "Error loading " + type + " properties from /WEB-INF/osgi/conf/" + defaultFileName, ex);
            } finally {
                closeQuietly(is);
            }
        } else {
            URL propURL = null;
            try {
                propURL = new URL(custom);
            } catch (final MalformedURLException ex) {
                final String message = type + " properties url {} could not be loaded as a URL. Checking WAR file.";
                if (this.logger.isTraceEnabled()) {
                    this.logger.trace(message, ex);
                } else {
                    this.logger.debug(message);
                }
            }

            InputStream is = null;
            try {
                if (propURL == null) {
                    is = this.servletContext.getResourceAsStream(custom);

                    // couldn't be loaded as a URL or as from WAR
                    if (is == null) {
                        throw new IllegalArgumentException("Error loading " + type + " properties from " + custom);
                    }

                    this.logger.info("{} properties url {} found inside WAR file.", type, custom);

                } else {
                    try {
                        is = propURL.openConnection().getInputStream();
                    } catch (final Exception ex) {
                        throw new IllegalArgumentException("Error loading " + type + " properties from " + custom, ex);
                    }
                }

                try {
                    props.load(is);
                } catch (final Exception ex) {
                    throw new IllegalArgumentException("Error loading " + type + " properties from " + custom, ex);
                }
            } finally {
                closeQuietly(is);
            }
        }

        return props;
    }

}
