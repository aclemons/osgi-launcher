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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import nz.caffe.osgi.launcher.AutoProcessor;
import nz.caffe.osgi.launcher.LoadCallback;
import nz.caffe.osgi.launcher.LoggingCallback;

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

    private final LoadCallback loadCallback;

    private final LoggingCallback loggingCallback;

    /**
     * @param loadCallback
     * @param loggingCallback
     */
    public BaseLauncher(final LoadCallback loadCallback, final LoggingCallback loggingCallback) {
        super();
        this.loadCallback = loadCallback;
        this.loggingCallback = loggingCallback;
    }

    /**
     * @param bundleDir
     * @param cacheDir
     * @param useSystemExit
     */
    public Framework launch(final String bundleDir, final String cacheDir, final boolean useSystemExit) {
        // Load system properties.
        loadSystemProperties();

        // Read configuration properties.
        Map<String, String> configProps = loadConfigProperties();
        // If no configuration properties were found, then create
        // an empty properties object.
        if (configProps == null) {
            System.err.println("No " + CONFIG_PROPERTIES_FILE_VALUE + " found.");
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
        String enableHook = configProps.get(SHUTDOWN_HOOK_PROP);
        Thread shutdownHook = null;
        if ((enableHook == null) || !enableHook.equalsIgnoreCase("false")) {
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
                        System.err.println("Error stopping framework: " + ex);
                    }
                }
            };
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }

        try {
            // Create an instance of the framework.
            FrameworkFactory factory = getFrameworkFactory();
            final Framework fwk = factory.newFramework(configProps);
            fwkRef.set(fwk);
            // Initialise the framework, but don't start it yet.
            fwk.init();
            // Use the system bundle context to process the auto-deploy
            // and auto-install/auto-start properties.
            AutoProcessor.process(configProps, fwk.getBundleContext(), getDefaultAutoDeployDirectory(),
                    this.loadCallback, this.loggingCallback);

            return fwk;
        } catch (Exception ex) {
            System.err.println("Could not create framework: " + ex);
            ex.printStackTrace();

            if (useSystemExit) {
                System.exit(0);
            }

            return null;
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
        URL url = BaseLauncher.class.getClassLoader()
                .getResource("META-INF/services/org.osgi.framework.launch.FrameworkFactory");
        if (url != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
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

        throw new Exception("Could not find framework factory.");
    }

    /**
     * Hook for subclasses to load system properties.
     */
    protected abstract void loadSystemProperties();

    /**
     * Hook for subclasses to load config properties.
     *
     * @return the loaded config properties
     */
    protected abstract Map<String, String> loadConfigProperties();

    /**
     * The default value for the auto-deploy directory when none is specified.
     *
     * @return
     */
    protected abstract String getDefaultAutoDeployDirectory();

    private static void copySystemProperties(Map<String, String> configProps) {
        for (Enumeration<?> e = System.getProperties().propertyNames(); e.hasMoreElements();) {
            String key = (String) e.nextElement();
            if (key.startsWith("caffe.") || key.startsWith("felix.") || key.startsWith("org.osgi.framework.")) {
                configProps.put(key, System.getProperty(key));
            }
        }
    }

    protected static void closeQuietly(final InputStream input) {
        try {
            if (input != null) {
                input.close();
            }
        } catch (final IOException ioe) {
            // ignore
        }
    }

    private static void closeQuietly(final Reader input) {
        try {
            if (input != null) {
                input.close();
            }
        } catch (final IOException ioe) {
            // ignore
        }
    }
}
