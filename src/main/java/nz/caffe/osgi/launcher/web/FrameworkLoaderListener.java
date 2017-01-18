/**
 * Copyright 2016-2017 Andrew Clemons <andrew.clemons@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nz.caffe.osgi.launcher.web;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nz.caffe.osgi.launcher.Launcher;
import nz.caffe.osgi.launcher.impl.FrameworkEventPollingCallable;

/**
 * This starts the framework when deploying inside a WAR file.
 */
public final class FrameworkLoaderListener implements ServletContextListener {

    /**
     * The 'current' framework instance, if the ContextLoader class is deployed
     * in the web app ClassLoader itself.
     */
    private static volatile Framework currentInstance;

    /**
     * Map from (thread context) ClassLoader to corresponding 'current'
     * Framework instance.
     */
    private static final Map<ClassLoader, Framework> currentInstancePerThread = new ConcurrentHashMap<ClassLoader, Framework>(
            1);

    /**
     * Context attribute to bind the Framework instance on successful startup.
     * <p>
     * Note: If the startup of the framework fails, this attribute can contain
     * an exception or error as value.
     */
    public static final String FRAMEWORK_ATTRIBUTE = FrameworkLoaderListener.class.getName() + ".FWK";

    /**
     * Name of servlet context parameter (i.e., {@value}) that can specify
     * whether to use the servlet context's temp dir attribute (
     * {@link ServletContext#TEMPDIR}) as the osdi cache dir. Default is false
     */
    public static final String USE_SERVLET_CONTEXT_TEMP_DIR_PARAM = "useServletContextTempDir";

    /**
     * Obtain the framework instance for the current thread (i.e. for the
     * current thread's context ClassLoader, which needs to be the web
     * application's ClassLoader).
     *
     * @return the current framework instance, or {@code null} if none found
     */
    public static Framework getCurrentWebApplicationContext() {
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl != null) {
            Framework ccpt = currentInstancePerThread.get(ccl);
            if (ccpt != null) {
                return ccpt;
            }
        }
        return currentInstance;
    }

    private Framework framework;

    private Future<?> future;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ExecutorService pool;

    private Thread shutdownHook;

    public void contextDestroyed(final ServletContextEvent sce) {
        sce.getServletContext().log("Stopping OSGi Framework");

        boolean interrupted = false;
        try {

            if (this.pool != null) {
                this.pool.shutdown();
            }

            try {
                if (this.framework != null) {
                    this.framework.stop();
                    this.framework.waitForStop(0);
                }

                if (this.future != null) {
                    this.future.get();
                }
            } catch (final InterruptedException e) {
                this.logger.warn("Interrupted waiting for framework to shutdown", e);

                interrupted = true;
            } catch (final Exception e) {
                this.logger.warn("Framework shutdown failed", e);
            }

        } finally {
            if (this.shutdownHook != null) {
                Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
            }

            final ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            if (ccl == FrameworkLoaderListener.class.getClassLoader()) {
                currentInstance = null;
            } else if (ccl != null) {
                currentInstancePerThread.remove(ccl);
            }

            sce.getServletContext().removeAttribute(FRAMEWORK_ATTRIBUTE);
            sce.getServletContext().removeAttribute(BundleContext.class.getName());
            if (interrupted) {
                Thread.currentThread().interrupt(); // reset flag
            }
        }
    }

    public void contextInitialized(final ServletContextEvent sce) {
        final ServletContext servletContext = sce.getServletContext();

        if (servletContext.getAttribute(FRAMEWORK_ATTRIBUTE) != null) {
            throw new IllegalStateException(
                    "Cannot initialise framework because there is already a framework instance present - "
                            + "check whether you have multiple FrameworkLoader* definitions in your web.xml!");
        }

        servletContext.log("Initialising OSGi Framework");

        if (this.logger.isInfoEnabled()) {
            this.logger.info("OSGi Framework: initialisation started");
        }

        final long startTime = System.currentTimeMillis();

        try {
            // Store context in local instance variable, to guarantee that
            // it is available on ServletContext shutdown.
            if (this.framework == null) {
                createFrameworkInstance(servletContext);
            }

            servletContext.setAttribute(FRAMEWORK_ATTRIBUTE, this.framework);
            servletContext.setAttribute(BundleContext.class.getName(), this.framework.getBundleContext());

            final ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            if (ccl == FrameworkLoaderListener.class.getClassLoader()) {
                currentInstance = this.framework;
            } else if (ccl != null) {
                currentInstancePerThread.put(ccl, this.framework);
            }

            this.logger.debug("Published Framework instance as ServletContext attribute with name [{}]",
                    FRAMEWORK_ATTRIBUTE);
            this.logger.debug("Published BundleContex as ServletContext attribute with name [{}]",
                    BundleContext.class.getName());

            if (this.logger.isInfoEnabled()) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                this.logger.info("OSGi Framework: initialisation completed in {} ms", Long.toString(elapsedTime));
            }

        } catch (final Exception ex) {
            this.logger.error("Framework initialisation failed", ex);
            servletContext.setAttribute(FRAMEWORK_ATTRIBUTE, ex);

            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }

            throw new IllegalStateException(ex);
        } catch (final Error err) {
            this.logger.error("Framework initialisation failed", err);
            servletContext.setAttribute(FRAMEWORK_ATTRIBUTE, err);
            throw err;
        }
    }

    private void createFrameworkInstance(final ServletContext servletContext) throws Exception {
        final String useServletContextTempDirConfig = servletContext
                .getInitParameter(USE_SERVLET_CONTEXT_TEMP_DIR_PARAM);

        final File cacheDir;
        if (Boolean.parseBoolean(useServletContextTempDirConfig)) {
            this.logger.debug("Configured to use servlet context's temp dir as the osgi cache dir.");

            final File servletTempDir = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
            if (servletTempDir == null) {
                cacheDir = null;

                this.logger.warn("Servlet context did not have an attribute name {}", ServletContext.TEMPDIR);

            } else {
                cacheDir = new File(servletTempDir, "felix-cache");

                this.logger.debug("Using osgi cache dir {}", cacheDir);
            }
        } else {
            this.logger.debug("Not using servlet context's temp dir as the osgi cache dir.");

            cacheDir = null;
        }

        final Launcher launcher = new WarLauncher(null, cacheDir == null ? null : cacheDir.getAbsolutePath(),
                new ServletContextCallback(servletContext), servletContext);

        launcher.launch();

        final Framework fwk = launcher.getFramework();
        final Thread hook = launcher.getShutdownHook();

        this.framework = fwk;
        this.shutdownHook = hook;

        fwk.start();

        final Callable<Object> worker = new FrameworkEventPollingCallable(fwk, hook);

        this.pool = Executors.newFixedThreadPool(1);

        this.future = this.pool.submit(worker);
    }
}
