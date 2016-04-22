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
package nz.caffe.osgi.launcher.impl;

import java.util.concurrent.Callable;

import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to poll for stop events which will restart the framework if
 * needed.
 */
public final class FrameworkEventPollingCallable implements Callable<Object> {

    private final Framework fwk;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Thread shutdownHook;

    /**
     * @param framework
     * @param shutdownHook
     */
    public FrameworkEventPollingCallable(Framework framework, Thread shutdownHook) {
        super();
        this.fwk = framework;
        this.shutdownHook = shutdownHook;
    }

    public Object call() throws Exception {

        try {
            while (true) {
                // Wait for framework to stop to exit the VM.
                final FrameworkEvent event = this.fwk.waitForStop(0);

                this.logger.debug("Got stop event {}", Integer.valueOf(event.getType()));

                if (event.getType() != FrameworkEvent.STOPPED_UPDATE) {
                    break;
                }

                this.logger.debug("Restarting framework");

                // Start the framework.
                this.fwk.start();
            }
        } finally {
            // remove the shutdown hook after stopping
            final boolean success = Runtime.getRuntime().removeShutdownHook(this.shutdownHook);

            if (success) {
                this.logger.debug("Removed shutdown hook from runtime.");
            } else {
                this.logger.debug("Shutdown hook already uninstalled from runtime.");
            }
        }

        return null;
    }
}
