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
package nz.caffe.osgi.launcher;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nz.caffe.osgi.launcher.impl.BaseLauncher;
import nz.caffe.osgi.launcher.impl.ServletContextCallback;
import nz.caffe.osgi.launcher.impl.Slf4jLoggingCallback;

/**
 * <p>
 * This class is the default way to instantiate and execute the framework. It is
 * not intended to be the only way to instantiate and execute the framework;
 * rather, it is one example of how to do so. When embedding the framework in a
 * host application, this class can serve as a simple guide of how to do so. It
 * may even be worthwhile to reuse some of its property handling capabilities.
 * </p>
 **/
public class WarLauncher extends BaseLauncher {

    /**
     * The default name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_VALUE = "/WEB-INF/osgi/bundle";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ServletContext servletContext;

    /**
     * @param servletContext
     */
    public WarLauncher(final ServletContext servletContext) {
        super(new ServletContextCallback(servletContext), new Slf4jLoggingCallback());
        this.servletContext = servletContext;
    }

    @Override
    protected String getDefaultAutoDeployDirectory() {
        return AUTO_DEPLOY_DIR_VALUE;
    }

    @Override
    protected Map<String, String> loadConfigProperties() {
        // The config properties file is either specified by a system
        // property or in WEB-INF/osgi/conf
        // Try to load it from one of these places.

        // Read the properties file.
        final Properties props = new Properties();

        // See if the property URL was specified as a property.
        final String custom = System.getProperty(BaseLauncher.CONFIG_PROPERTIES_PROP);

        if (custom == null) {
            final InputStream is = this.servletContext
                    .getResourceAsStream("/WEB-INF/osgi/conf/" + BaseLauncher.CONFIG_PROPERTIES_FILE_VALUE);

            if (is == null) {
                this.logger.trace("WAR file does not contain " + "/WEB-INF/osgi/conf/{}. Skipping loading "
                        + "config properties.", BaseLauncher.CONFIG_PROPERTIES_FILE_VALUE);
                return null;
            }

            try {
                props.load(is);
            } catch (final Exception ex) {
                // TODO: do not swallow errors
                this.logger.warn("Error loading config properties from " + "/WEB-INF/osgi/conf/"
                        + BaseLauncher.CONFIG_PROPERTIES_FILE_VALUE, ex);
                closeQuietly(is);
                return null;
            }
        } else {
            URL propURL = null;
            try {
                propURL = new URL(custom);
            } catch (final MalformedURLException ex) {
                if (this.logger.isTraceEnabled()) {
                    this.logger.trace("Config properties url " + propURL + " could not be "
                            + "loaded as a URL. Checking WAR file.", ex);
                } else {
                    this.logger.debug("Config properties url {} could not be " + "loaded as a URL. Checking WAR file.",
                            propURL);
                }
            }

            InputStream is = null;
            try {
                if (propURL == null) {
                    is = this.servletContext.getResourceAsStream(custom);

                    if (is == null) {
                        // TODO: do not swallow errors
                        this.logger.warn("Error loading config properties from {}", custom);
                        return null;
                    }

                } else {
                    try {
                        is = propURL.openConnection().getInputStream();
                    } catch (final FileNotFoundException ex) {
                        // Ignore file not found.
                        return null;
                    } catch (Exception ex) {
                        // TODO: do not swallow exceptions
                        this.logger.warn("Error loading config properties from " + propURL, ex);
                        return null;
                    }
                }

                try {
                    props.load(is);
                } catch (final Exception ex) {
                    // TODO: do not swallow errors
                    this.logger.warn("Error loading system properties from " + propURL, ex);
                    return null;
                }
            } finally {
                closeQuietly(is);
            }
        }

        // Perform variable substitution for system properties and
        // convert to dictionary.
        Map<String, String> map = new HashMap<String, String>();
        for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            map.put(name, Util.substVars(props.getProperty(name), name, null, props));
        }

        return map;
    }

    /**
     * <p>
     * Loads the properties in the system property file associated with the
     * framework installation into <tt>System.setProperty()</tt>. These
     * properties are not directly used by the framework in anyway. By default,
     * the system property file is located in the <tt>conf/</tt> directory of
     * the WEB-INF/osgi directory in the war file and is called
     * "<tt>system.properties</tt>". The precise file from which to load system
     * properties can be set by initialising the
     * "<tt>caffe.system.properties</tt>" system property to an arbitrary URL.
     * </p>
     **/
    @Override
    protected void loadSystemProperties() {
        // The system properties file is either specified by a system
        // property or in WEB-INF/osgi/conf
        // Try to load it from one of these places.

        // Read the properties file.
        final Properties props = new Properties();

        // See if the property URL was specified as a property.
        final String custom = System.getProperty(BaseLauncher.SYSTEM_PROPERTIES_PROP);

        if (custom == null) {
            final InputStream is = this.servletContext
                    .getResourceAsStream("/WEB-INF/osgi/conf/" + BaseLauncher.SYSTEM_PROPERTIES_FILE_VALUE);

            if (is == null) {
                this.logger.trace("/WAR file does not contain " + "WEB-INF/osgi/conf/{}. Skipping loading "
                        + "system properties.", BaseLauncher.SYSTEM_PROPERTIES_FILE_VALUE);
                return;
            }

            try {
                props.load(is);
            } catch (final Exception ex) {
                // TODO: do not swallow errors
                this.logger.warn("Error loading system properties from " + "/WEB-INF/conf/"
                        + BaseLauncher.SYSTEM_PROPERTIES_FILE_VALUE, ex);
                closeQuietly(is);
                return;
            }
        } else {
            URL propURL = null;
            try {
                propURL = new URL(custom);
            } catch (final MalformedURLException ex) {
                if (this.logger.isTraceEnabled()) {
                    this.logger.trace("System properties url " + propURL + " could not be "
                            + "loaded as a URL. Checking WAR file.", ex);
                } else {
                    this.logger.debug("System properties url {} could not be " + "loaded as a URL. Checking WAR file.",
                            propURL);
                }
            }

            InputStream is = null;
            try {
                if (propURL == null) {
                    is = this.servletContext.getResourceAsStream(custom);

                    if (is == null) {
                        // TODO: do not swallow errors
                        this.logger.warn("Error loading system properties from " + custom);
                        return;
                    }

                } else {
                    try {
                        is = propURL.openConnection().getInputStream();
                    } catch (final FileNotFoundException ex) {
                        // Ignore file not found.
                        return;
                    } catch (Exception ex) {
                        // TODO: do not swallow exceptions
                        this.logger.warn("Error loading system properties from " + propURL, ex);
                        return;
                    }
                }

                try {
                    props.load(is);
                } catch (final Exception ex) {
                    // TODO: do not swallow errors
                    this.logger.warn("Error loading system properties from " + propURL, ex);
                    return;
                }
            } finally {
                closeQuietly(is);
            }
        }

        // Perform variable substitution on specified properties.
        for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            System.setProperty(name, Util.substVars(props.getProperty(name), name, null, null));
        }
    }

}
