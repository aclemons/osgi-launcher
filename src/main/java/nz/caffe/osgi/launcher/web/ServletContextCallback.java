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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContext;

import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nz.caffe.osgi.launcher.LoadCallback;

/**
 * Allow loading bundles from inside a WAR file.
 */
public final class ServletContextCallback implements LoadCallback {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ServletContext servletContext;

    /**
     * @param servletContext
     */
    public ServletContextCallback(final ServletContext servletContext) {
        super();
        this.servletContext = servletContext;
    }

    public List<String> listBundles(final String directory) {
        final Set<String> files = this.servletContext.getResourcePaths(directory);

        final List<String> jarList = new ArrayList<String>();

        if (files != null) {
            for (final String file : files) {
                if (file.endsWith(".jar") || file.endsWith(".war")) {
                    jarList.add(file);
                } else {
                    this.logger.debug("Filtered {} from deploy list for directory {}", file, directory);
                }
            }
        }

        Collections.sort(jarList);

        return jarList;
    }

    public InputStream openStream(final String bundle) throws BundleException {
        final InputStream stream = this.servletContext.getResourceAsStream(bundle);

        if (stream == null) {
            throw new BundleException("No such resource " + bundle, BundleException.UNSPECIFIED);

        }

        return stream;
    }
}
