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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.service.startlevel.StartLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nz.caffe.osgi.launcher.LoadCallback;

/**
 */
public class AutoProcessor {
    /**
     * The property name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_PROPERTY = "caffe.auto.deploy.dir";
    /**
     * The property name used to specify auto-deploy actions.
     **/
    public static final String AUTO_DEPLOY_ACTION_PROPERTY = "caffe.auto.deploy.action";
    /**
     * The property name used to specify auto-deploy start level.
     **/
    public static final String AUTO_DEPLOY_STARTLEVEL_PROPERTY = "caffe.auto.deploy.startlevel";
    /**
     * The name used for the auto-deploy install action.
     **/
    public static final String AUTO_DEPLOY_INSTALL_VALUE = "install";
    /**
     * The name used for the auto-deploy start action.
     **/
    public static final String AUTO_DEPLOY_START_VALUE = "start";
    /**
     * The name used for the auto-deploy update action.
     **/
    public static final String AUTO_DEPLOY_UPDATE_VALUE = "update";
    /**
     * The name used for the auto-deploy uninstall action.
     **/
    public static final String AUTO_DEPLOY_UNINSTALL_VALUE = "uninstall";
    /**
     * The property name prefix for the launcher's auto-install property.
     **/
    public static final String AUTO_INSTALL_PROP = "caffe.auto.install";
    /**
     * The property name prefix for the launcher's auto-start property.
     **/
    public static final String AUTO_START_PROP = "caffe.auto.start";

    private static final Logger LOG = LoggerFactory.getLogger(AutoProcessor.class);

    /**
     * Used to instigate auto-deploy directory process and
     * auto-install/auto-start configuration property processing during.
     *
     * @param configMap
     *            Map of configuration properties.
     * @param context
     *            The system bundle context.
     * @param defaultAutoDeployDir
     * @param callback
     *            the callback to use to list and process files from a dir
     **/
    public static void process(final Map<String, String> configMap, final BundleContext context,
            final String defaultAutoDeployDir, final LoadCallback callback) {
        final Map<String, String> safeConfigMap = (configMap == null) ? new HashMap<String, String>() : configMap;
        processAutoDeploy(safeConfigMap, context, defaultAutoDeployDir, callback);
        processAutoProperties(safeConfigMap, context);
    }

    /**
     * <p>
     * Processes bundles in the auto-deploy directory, performing the specified
     * deploy actions.
     * </p>
     */
    private static void processAutoDeploy(final Map<String, String> configMap, final BundleContext context,
            final String defaultAutoDeployDir, final LoadCallback callback) {

        // Determine if auto deploy actions to perform.
        String action = configMap.get(AUTO_DEPLOY_ACTION_PROPERTY);
        action = (action == null) ? "" : action;

        final List<String> actionList = new ArrayList<String>();

        final StringTokenizer st = new StringTokenizer(action, ",");

        while (st.hasMoreTokens()) {
            final String s = st.nextToken().trim().toLowerCase(Locale.ENGLISH);
            if (s.equals(AUTO_DEPLOY_INSTALL_VALUE) || s.equals(AUTO_DEPLOY_START_VALUE)
                    || s.equals(AUTO_DEPLOY_UPDATE_VALUE) || s.equals(AUTO_DEPLOY_UNINSTALL_VALUE)) {
                actionList.add(s);
            }
        }

        // Perform auto-deploy actions.
        if (actionList.size() > 0) {
            // Retrieve the Start Level service, since it will be needed
            // to set the start level of the installed bundles.
            final StartLevel sl = (StartLevel) context
                    .getService(context.getServiceReference(org.osgi.service.startlevel.StartLevel.class.getName()));

            // Get start level for auto-deploy bundles.
            int startLevel = sl.getInitialBundleStartLevel();
            if (configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERTY) != null) {
                try {
                    startLevel = Integer.parseInt(configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERTY).toString());
                } catch (NumberFormatException ex) {
                    // Ignore and keep default level.
                }
            }

            // Get list of already installed bundles as a map.
            final Map<String, Bundle> installedBundleMap = new HashMap<String, Bundle>();
            final Bundle[] bundles = context.getBundles();
            for (final Bundle bundle : bundles) {
                installedBundleMap.put(bundle.getLocation(), bundle);
            }

            // Get the auto deploy directory.
            String autoDir = configMap.get(AUTO_DEPLOY_DIR_PROPERTY);
            autoDir = (autoDir == null) ? defaultAutoDeployDir : autoDir;
            // Look in the specified bundle directory to create a list
            // of all JAR files to install.

            final List<String> jarList = callback.listBundles(autoDir);

            // Install bundle JAR files and remember the bundle objects.
            final List<Bundle> startBundleList = new ArrayList<Bundle>();
            for (final String location : jarList) {
                // Look up the bundle by location, removing it from
                // the map of installed bundles so the remaining bundles
                // indicate which bundles may need to be uninstalled.
                Bundle b = installedBundleMap.remove(location);

                try {
                    // If the bundle is not already installed, then install it
                    // if the 'install' action is present.
                    if ((b == null) && actionList.contains(AUTO_DEPLOY_INSTALL_VALUE)) {
                        final InputStream stream = callback.openStream(location);
                        try {
                            b = context.installBundle(location, stream);
                        } finally {
                            try {
                                stream.close();
                            } catch (IOException e) {
                                // ignored
                            }
                        }

                    }
                    // If the bundle is already installed, then update it
                    // if the 'update' action is present.
                    else if ((b != null) && actionList.contains(AUTO_DEPLOY_UPDATE_VALUE)) {
                        b.update();
                    }

                    // If we have found and/or successfully installed a bundle,
                    // then add it to the list of bundles to potentially start
                    // and also set its start level accordingly.
                    if ((b != null) && !isFragment(b)) {
                        startBundleList.add(b);
                        sl.setBundleStartLevel(b, startLevel);
                    }
                } catch (final BundleException ex) {
                    LOG.error("Auto-deploy install failed for " + location + ".", ex);
                }
            }

            // Uninstall all bundles not in the auto-deploy directory if
            // the 'uninstall' action is present.
            if (actionList.contains(AUTO_DEPLOY_UNINSTALL_VALUE)) {
                for (final Entry<String, Bundle> entry : installedBundleMap.entrySet()) {
                    final Bundle b = entry.getValue();
                    if (b.getBundleId() != 0) {
                        try {
                            b.uninstall();
                        } catch (final BundleException ex) {
                            LOG.error("Auto-deploy uninstall failed for " + b.getLocation() + ".", ex);
                        }
                    }
                }
            }

            // Start all installed and/or updated bundles if the 'start'
            // action is present.
            if (actionList.contains(AUTO_DEPLOY_START_VALUE)) {
                for (final Bundle bundle : startBundleList) {
                    try {
                        bundle.start();
                    } catch (final BundleException ex) {
                        LOG.error("Auto-deploy start failed for " + bundle.getLocation() + ".", ex);
                    }
                }
            }
        }
    }

    /**
     * <p>
     * Processes the auto-install and auto-start properties from the specified
     * configuration properties.
     * </p>
     */
    private static void processAutoProperties(final Map<String, String> configMap, final BundleContext context) {

        // Retrieve the Start Level service, since it will be needed
        // to set the start level of the installed bundles.
        final StartLevel sl = (StartLevel) context
                .getService(context.getServiceReference(org.osgi.service.startlevel.StartLevel.class.getName()));

        // Retrieve all auto-install and auto-start properties and install
        // their associated bundles. The auto-install property specifies a
        // space-delimited list of bundle URLs to be automatically installed
        // into each new profile, while the auto-start property specifies
        // bundles to be installed and started. The start level to which the
        // bundles are assigned is specified by appending a ".n" to the
        // property name, where "n" is the desired start level for the list
        // of bundles. If no start level is specified, the default start
        // level is assumed.
        for (final Entry<String, String> entry : configMap.entrySet()) {
            final String lowerKey = entry.getKey().toLowerCase(Locale.ENGLISH);

            // Ignore all keys that are not an auto property.
            if (!lowerKey.startsWith(AUTO_INSTALL_PROP) && !lowerKey.startsWith(AUTO_START_PROP)) {
                continue;
            }

            // If the auto property does not have a start level,
            // then assume it is the default bundle start level, otherwise
            // parse the specified start level.
            int startLevel = sl.getInitialBundleStartLevel();
            if (!lowerKey.equals(AUTO_INSTALL_PROP) && !lowerKey.equals(AUTO_START_PROP)) {
                try {
                    startLevel = Integer.parseInt(lowerKey.substring(lowerKey.lastIndexOf('.') + 1));
                } catch (final NumberFormatException ex) {
                    LOG.warn("Invalid auto-start property " + lowerKey + ".", ex);
                }
            }

            // Parse and install the bundles associated with the key.
            final StringTokenizer st = new StringTokenizer(entry.getValue(), "\" ", true);
            for (String location = nextLocation(st); location != null; location = nextLocation(st)) {
                try {
                    final Bundle b = context.installBundle(location, null);
                    sl.setBundleStartLevel(b, startLevel);
                } catch (final Exception ex) {
                    LOG.error("Auto-properties install for " + location + " failed.", ex);
                }
            }
        }

        // Now loop through the auto-start bundles and start them.
        for (final Entry<String, String> entry : configMap.entrySet()) {
            final String lowerKey = entry.getKey().toLowerCase(Locale.ENGLISH);
            if (lowerKey.startsWith(AUTO_START_PROP)) {
                final StringTokenizer st = new StringTokenizer(entry.getValue(), "\" ", true);

                for (String location = nextLocation(st); location != null; location = nextLocation(st)) {
                    // Installing twice just returns the same bundle.
                    try {
                        final Bundle b = context.installBundle(location, null);
                        if (b != null) {
                            b.start();
                        }
                    } catch (final Exception ex) {
                        LOG.error("Auto-properties start for " + location + " failed.", ex);
                    }
                }
            }
        }
    }

    private static String nextLocation(final StringTokenizer st) {
        String retVal = null;

        if (st.countTokens() > 0) {
            String tokenList = "\" ";
            StringBuilder tokBuf = new StringBuilder(10);
            String tok = null;
            boolean inQuote = false;
            boolean tokStarted = false;
            boolean exit = false;
            while ((st.hasMoreTokens()) && (!exit)) {
                tok = st.nextToken(tokenList);
                if (tok.equals("\"")) {
                    inQuote = !inQuote;
                    if (inQuote) {
                        tokenList = "\"";
                    } else {
                        tokenList = "\" ";
                    }

                } else if (tok.equals(" ")) {
                    if (tokStarted) {
                        retVal = tokBuf.toString();
                        tokStarted = false;
                        tokBuf = new StringBuilder(10);
                        exit = true;
                    }
                } else {
                    tokStarted = true;
                    tokBuf.append(tok.trim());
                }
            }

            // Handle case where end of token stream and
            // still got data
            if ((!exit) && (tokStarted)) {
                retVal = tokBuf.toString();
            }
        }

        return retVal;
    }

    private static boolean isFragment(final Bundle bundle) {
        return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
    }
}