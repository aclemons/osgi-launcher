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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nz.caffe.osgi.launcher.LoggingCallback;

/**
 * Default implementation for the command line launcher. It uses stderr to print
 * its error messages.
 */
public final class Slf4jLoggingCallback implements LoggingCallback {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void error(final String message, final Throwable ex) {
        this.logger.error(message, ex);
    }

    public void warn(final String message, final Throwable ex) {
        this.logger.warn(message, ex);
    }

}
