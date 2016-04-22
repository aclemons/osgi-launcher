/**
 * Copyright 2016 Andrew Clemons <andrew.clemons@gmail.com>
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
package nz.caffe.osgi.launcher;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nz.caffe.osgi.launcher.impl.ServletContextCallback;

/**
 * This starts the framework when deploying inside a WAR file.
 */
public class FrameworkLoaderListener implements ServletContextListener {

    /**
     * Context attribute to bind the Framework instance on successful startup.
     * <p>
     * Note: If the startup of the framework fails, this attribute can contain
     * an exception or error as value.
     */
    public static final String FRAMEWORK_ATTRIBUTE = FrameworkLoaderListener.class.getName() + ".FWK";

    private Framework framework;

    private Future<?> future;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ExecutorService pool;

    private Thread shutdownHook;

    public void contextDestroyed(final ServletContextEvent sce) {
        sce.getServletContext().log("Stopping OSGi Framework");

        if (this.pool != null) {
            this.pool.shutdown();
        }

        try {
            if (this.framework != null) {
                this.framework.stop();
                this.framework.waitForStop(0);
            }
        } catch (final Exception e) {
            sce.getServletContext().log("Framework shutdown failed", e);
        } finally {
            sce.getServletContext().removeAttribute(FRAMEWORK_ATTRIBUTE);
        }

        if (this.future != null) {
            try {
                this.future.get();
            } catch (final InterruptedException e) {
                this.logger.warn("Interrupted waiting for framework to shutdown", e);
            } catch (@SuppressWarnings("unused") final ExecutionException e) {
                // ignored
            }
        }

        if (this.shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
        }
    }

    public void contextInitialized(final ServletContextEvent sce) {

        // TODO: make cache dir configurable through web.xml

        final File cacheDir;
        final File servletTempDir = (File) sce.getServletContext().getAttribute(ServletContext.TEMPDIR);
        if (servletTempDir == null) {
            cacheDir = null;
        } else {
            cacheDir = new File(servletTempDir, "felix-cache");

            sce.getServletContext().log("Using cache-dir " + cacheDir);
        }

        final Framework fwk;
        try {
            fwk = new WarLauncher(new ServletContextCallback(sce.getServletContext()), sce.getServletContext())
                    .launch(null, cacheDir == null ? null : cacheDir.getAbsolutePath(), false);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }

        try {
            fwk.start();
        } catch (BundleException e) {
            throw new IllegalStateException(e);
        }

        this.framework = fwk;

        final Callable<Object> worker = new Callable<Object>() {

            public Object call() throws Exception {
                boolean first = true;
                FrameworkEvent event;
                do {
                    if (!first) {
                        // Start the framework.
                        fwk.start();
                    }
                    // Wait for framework to stop to exit the VM.
                    event = fwk.waitForStop(0);
                    first = false;
                }
                // If the framework was updated, then restart it.
                while (event.getType() == FrameworkEvent.STOPPED_UPDATE);

                return null;
            }
        };

        this.pool = Executors.newFixedThreadPool(1);

        this.future = this.pool.submit(worker);
    }

}