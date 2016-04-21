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
package nz.caffe.osgi.launcher.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nz.caffe.osgi.launcher.LoadCallback;

/**
 * <p>
 * This class is the default way to instantiate and execute the framework. It is
 * not intended to be the only way to instantiate and execute the framework;
 * rather, it is one example of how to do so. When embedding the framework in a
 * host application, this class can serve as a simple guide of how to do so. It
 * may even be worthwhile to reuse some of its property handling capabilities.
 * </p>
 **/
public abstract class BaseLauncher {
    /**
     * The property name used to specify whether the launcher should install a
     * shutdown hook.
     **/
    public static final String SHUTDOWN_HOOK_PROP = "caffe.shutdown.hook";
    /**
     * The property name used to specify an URL to the system property file.
     **/
    public static final String SYSTEM_PROPERTIES_PROP = "caffe.system.properties";
    /**
     * The default name used for the system properties file.
     **/
    public static final String SYSTEM_PROPERTIES_FILE_VALUE = "system.properties";
    /**
     * The property name used to specify an URL to the configuration property
     * file to be used for the created the framework instance.
     **/
    public static final String CONFIG_PROPERTIES_PROP = "caffe.config.properties";
    /**
     * The default name used for the configuration properties file.
     **/
    public static final String CONFIG_PROPERTIES_FILE_VALUE = "config.properties";
    /**
     * Name of the configuration directory.
     */
    public static final String CONFIG_DIRECTORY = "conf";

    private static final String DELIM_START = "${";

    private static final String DELIM_STOP = "}";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected static void closeQuietly(final InputStream input) {
        try {
            if (input != null) {
                input.close();
            }
        } catch (@SuppressWarnings("unused") final IOException ioe) {
            // ignore
        }
    }

    private static void closeQuietly(final Reader input) {
        try {
            if (input != null) {
                input.close();
            }
        } catch (@SuppressWarnings("unused") final IOException ioe) {
            // ignore
        }
    }

    private static void copySystemProperties(final Map<String, String> configProps) {
        for (final Entry<Object, Object> entry : System.getProperties().entrySet()) {
            final String key = (String) entry.getKey();
            if (key.startsWith("caffe.") || key.startsWith("felix.") || key.startsWith("org.osgi.framework.")) {
                configProps.put(key, (String) entry.getValue());
            }
        }
    }

    /**
     * Simple method to parse META-INF/services file for framework factory.
     * Currently, it assumes the first non-commented line is the class name of
     * the framework factory implementation.
     *
     * @return The created <tt>FrameworkFactory</tt> instance.
     * @throws Exception
     *             if any errors occur.
     **/
    private static FrameworkFactory getFrameworkFactory() throws Exception {
        final URL url = BaseLauncher.class.getClassLoader()
                .getResource("META-INF/services/org.osgi.framework.launch.FrameworkFactory");

        if (url != null) {
            final BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            try {
                for (String s = br.readLine(); s != null; s = br.readLine()) {
                    s = s.trim();
                    // Try to load first non-empty, non-commented line.
                    if ((s.length() > 0) && (s.charAt(0) != '#')) {
                        return (FrameworkFactory) Class.forName(s).newInstance();
                    }
                }
            } finally {
                closeQuietly(br);
            }
        }

        throw new IllegalStateException("Could not find framework factory.");
    }

    /**
     * <p>
     * This method performs property variable substitution on the specified
     * value. If the specified value contains the syntax
     * <tt>${&lt;prop-name&gt;}</tt>, where <tt>&lt;prop-name&gt;</tt> refers to
     * either a configuration property or a system property, then the
     * corresponding property value is substituted for the variable placeholder.
     * Multiple variable placeholders may exist in the specified value as well
     * as nested variable placeholders, which are substituted from inner most to
     * outer most. Configuration properties override system properties.
     * </p>
     *
     * @param val
     *            The string on which to perform property substitution.
     * @param currentKey
     *            The key of the property being evaluated used to detect cycles.
     * @param cycleMap
     *            Map of variable references used to detect nested cycles.
     * @param configProps
     *            Set of configuration properties.
     * @return The value of the specified string after system property
     *         substitution.
     * @throws IllegalArgumentException
     *             If there was a syntax error in the property placeholder
     *             syntax or a recursive variable reference.
     **/
    protected static String substVars(String val, String currentKey, final Map<String, String> cycleMap,
            Properties configProps) throws IllegalArgumentException {
        // If there is currently no cycle map, then create
        // one for detecting cycles for this invocation.
        final Map<String, String> cycles;
        if (cycleMap == null) {
            cycles = new HashMap<String, String>();
        } else {
            cycles = cycleMap;
        }

        // Put the current key in the cycle map.
        cycles.put(currentKey, currentKey);

        // Assume we have a value that is something like:
        // "leading ${foo.${bar}} middle ${baz} trailing"

        // Find the first ending '}' variable delimiter, which
        // will correspond to the first deepest nested variable
        // placeholder.
        int stopDelim = -1;
        int startDelim = -1;

        do {
            stopDelim = val.indexOf(DELIM_STOP, stopDelim + 1);
            // If there is no stopping delimiter, then just return
            // the value since there is no variable declared.
            if (stopDelim < 0) {
                return val;
            }
            // Try to find the matching start delimiter by
            // looping until we find a start delimiter that is
            // greater than the stop delimiter we have found.
            startDelim = val.indexOf(DELIM_START);
            // If there is no starting delimiter, then just return
            // the value since there is no variable declared.
            if (startDelim < 0) {
                return val;
            }
            while (stopDelim >= 0) {
                int idx = val.indexOf(DELIM_START, startDelim + DELIM_START.length());
                if ((idx < 0) || (idx > stopDelim)) {
                    break;
                } else if (idx < stopDelim) {
                    startDelim = idx;
                }
            }
        } while ((startDelim > stopDelim) && (stopDelim >= 0));

        // At this point, we have found a variable placeholder so
        // we must perform a variable substitution on it.
        // Using the start and stop delimiter indices, extract
        // the first, deepest nested variable placeholder.
        final String variable = val.substring(startDelim + DELIM_START.length(), stopDelim);

        // Verify that this is not a recursive variable reference.
        if (cycles.get(variable) != null) {
            throw new IllegalArgumentException("recursive variable reference: " + variable);
        }

        // Get the value of the deepest nested variable placeholder.
        // Try to configuration properties first.
        String substValue = (configProps != null) ? configProps.getProperty(variable, null) : null;
        if (substValue == null) {
            // Ignore unknown property values.
            substValue = System.getProperty(variable, "");
        }

        // Remove the found variable from the cycle map, since
        // it may appear more than once in the value and we don't
        // want such situations to appear as a recursive reference.
        cycles.remove(variable);

        // Append the leading characters, the substituted value of
        // the variable, and the trailing characters to get the new
        // value.
        final String val2 = val.substring(0, startDelim) + substValue
                + val.substring(stopDelim + DELIM_STOP.length(), val.length());

        // Now perform substitution again, since there could still
        // be substitutions to make.
        final String val3 = substVars(val2, currentKey, cycles, configProps);

        // Return the value.
        return val3;
    }

    protected final LoadCallback loadCallback;

    /**
     * @param loadCallback
     */
    public BaseLauncher(final LoadCallback loadCallback) {
        super();
        this.loadCallback = loadCallback;
    }

    /**
     * The default value for the auto-deploy directory when none is specified.
     *
     * @return
     */
    protected abstract String getDefaultAutoDeployDirectory();

    /**
     * @param bundleDir
     * @param cacheDir
     * @param useSystemExit
     * @return the framework instance
     * @throws Exception
     */
    public Framework launch(final String bundleDir, final String cacheDir, final boolean useSystemExit)
            throws Exception {
        // Load system properties.
        loadSystemProperties();

        // Read configuration properties.
        Map<String, String> configProps = loadConfigProperties();
        // If no configuration properties were found, then create
        // an empty properties object.
        if (configProps == null) {
            this.logger.warn("No {} found", CONFIG_PROPERTIES_FILE_VALUE);
            configProps = new HashMap<String, String>();
        }

        // Copy framework properties from the system properties.
        copySystemProperties(configProps);

        // If there is a passed in bundle auto-deploy directory, then
        // that overwrites anything in the config file.
        if (bundleDir != null) {
            configProps.put(AutoProcessor.AUTO_DEPLOY_DIR_PROPERTY, bundleDir);
        }

        // If there is a passed in bundle cache directory, then
        // that overwrites anything in the config file.
        if (cacheDir != null) {
            configProps.put(Constants.FRAMEWORK_STORAGE, cacheDir);
        }

        final AtomicReference<Framework> fwkRef = new AtomicReference<Framework>();

        // If enabled, register a shutdown hook to make sure the framework is
        // cleanly shutdown when the VM exits.
        final String enableHook = configProps.get(SHUTDOWN_HOOK_PROP);
        final Thread shutdownHook;
        if (enableHook == null || !enableHook.equalsIgnoreCase("false")) {
            shutdownHook = new Thread("Framework Shutdown Hook") {
                @Override
                public void run() {
                    try {
                        final Framework fwk = fwkRef.get();
                        if (fwk != null) {
                            fwk.stop();
                            fwk.waitForStop(0);
                        }
                    } catch (final Exception ex) {
                        BaseLauncher.this.logger.warn("Error stopping framework", ex);
                    }
                }
            };
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } else {
            shutdownHook = null;
        }

        // Create an instance of the framework.
        final FrameworkFactory factory = getFrameworkFactory();
        final Framework fwk = factory.newFramework(configProps);

        fwkRef.set(fwk);
        // Initialise the framework, but don't start it yet.
        fwk.init();

        // Use the system bundle context to process the auto-deploy
        // and auto-install/auto-start properties.
        AutoProcessor.process(configProps, fwk.getBundleContext(), getDefaultAutoDeployDirectory(), this.loadCallback);

        return fwk;
    }

    /**
     * <p>
     * Loads the configuration properties in the configuration property file
     * associated with the framework installation; these properties are
     * accessible to the framework and to bundles and are intended for
     * configuration purposes. By default, the configuration property file is
     * located in the <tt>conf/</tt> directory of the current user directory and
     * is called "<tt>config.properties</tt>". The precise file from which to
     * load configuration properties can be set by initialising the
     * "<tt>caffe.config.properties</tt>" system property to an arbitrary URL.
     * </p>
     *
     * @return A <tt>Properties</tt> instance or <tt>null</tt> if there was an
     *         error.
     **/
    private Map<String, String> loadConfigProperties() {

        final Properties props = loadPropertiesFile(BaseLauncher.CONFIG_PROPERTIES_PROP,
                BaseLauncher.CONFIG_PROPERTIES_FILE_VALUE, "config");

        if (props == null) {
            return null;
        }

        // Perform variable substitution for system properties and
        // convert to dictionary.
        final Map<String, String> map = new HashMap<String, String>();

        for (final Entry<Object, Object> entry : props.entrySet()) {
            final String name = (String) entry.getKey();
            map.put(name, substVars((String) entry.getValue(), name, null, props));
        }

        return map;
    }

    /**
     * Hook for subclasses to load a properties file
     *
     * @param systemPropertyName
     *            the system property name
     * @param defaultFileName
     *            the default file name
     * @param type
     *            the type (config|system)
     * @return the loaded properties
     */
    protected abstract Properties loadPropertiesFile(final String systemPropertyName, final String defaultFileName,
            final String type);

    /**
     * <p>
     * Loads the properties in the system property file associated with the
     * framework installation into <tt>System.setProperty()</tt>. These
     * properties are not directly used by the framework in anyway. By default,
     * the system property file is located in the <tt>conf/</tt> directory of
     * the current user directory and is called "<tt>system.properties</tt>".
     * The precise file from which to load system properties can be set by
     * initialising the "<tt>caffe.system.properties</tt>" system property to an
     * arbitrary URL.
     * </p>
     **/
    private void loadSystemProperties() {
        final Properties props = loadPropertiesFile(BaseLauncher.SYSTEM_PROPERTIES_PROP,
                BaseLauncher.SYSTEM_PROPERTIES_FILE_VALUE, "system");

        if (props == null) {
            return;
        }

        // Perform variable substitution on specified properties.
        for (final Entry<Object, Object> entry : props.entrySet()) {
            final String name = (String) entry.getKey();
            System.setProperty(name, substVars((String) entry.getValue(), name, null, null));
        }
    }
}
