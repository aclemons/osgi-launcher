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
package nz.caffe.osgi.launcher.console;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import nz.caffe.osgi.launcher.LoadCallback;
import nz.caffe.osgi.launcher.impl.BaseLauncher;

/**
 * <p>
 * This class provides the default way to instantiate and execute an osgi
 * runtime for console (standalone) based applications.
 * </p>
 **/
public class ConsoleLauncher extends BaseLauncher {

    /**
     * The default name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_VALUE = "bundle";

    /**
     * @param bundleDir
     * @param cacheDir
     * @param loadCallback
     */
    public ConsoleLauncher(final String bundleDir, final String cacheDir, final LoadCallback loadCallback) {
        super(bundleDir, cacheDir, loadCallback);
    }

    @Override
    protected String getDefaultAutoDeployDirectory() {
        return AUTO_DEPLOY_DIR_VALUE;
    }

    @Override
    protected Properties loadPropertiesFile(final String systemPropertyName, final String defaultFileName,
            final String type) {
        // The config properties file is either specified by a system
        // property or in $user.dir/conf
        // Try to load it from one of these places.

        // See if the property URL was specified as a property.
        final URL propURL;
        final String custom = System.getProperty(systemPropertyName);
        if (custom == null) {
            // use the current directory as default.
            final File confDir = new File(System.getProperty("user.dir"), CONFIG_DIRECTORY);
            final File propertiesFile = new File(confDir, defaultFileName);

            if (!propertiesFile.exists()) {
                this.logger.debug("No {} found", defaultFileName);
                return null;
            }

            try {
                propURL = propertiesFile.toURI().toURL();
            } catch (MalformedURLException ex) {
                throw new IllegalArgumentException(
                        "Error loading " + type + " properties from " + propertiesFile.getAbsolutePath(), ex);
            }
        } else {
            try {
                propURL = new URL(custom);
            } catch (final MalformedURLException ex) {
                throw new IllegalArgumentException("Error loading " + type + " properties from " + custom, ex);
            }
        }

        // Read the properties file.
        final Properties props = new Properties();
        InputStream is = null;
        try {
            // Try to load config.properties.
            is = propURL.openConnection().getInputStream();
            props.load(is);
        } catch (final Exception ex) {
            throw new IllegalArgumentException("Error loading " + type + " properties from " + propURL, ex);
        } finally {
            closeQuietly(is);
        }

        return props;
    }

}
