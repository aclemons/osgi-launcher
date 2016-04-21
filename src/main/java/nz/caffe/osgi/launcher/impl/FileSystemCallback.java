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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nz.caffe.osgi.launcher.LoadCallback;

/**
 * Allow loading bundles from simple directories on the file system.
 */
public final class FileSystemCallback implements LoadCallback {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public List<String> listBundles(final String directory) {
        final File[] files = new File(directory).listFiles();

        final List<String> jarList = new ArrayList<String>();

        if (files != null) {
            Arrays.sort(files);

            for (final File file : files) {
                if (file.getName().endsWith(".jar") || file.getName().endsWith(".war")) {
                    jarList.add(file.getAbsolutePath());
                } else {
                    this.logger.debug("Filtered {} from deploy list for directory {}", file, directory);
                }
            }
        }

        return jarList;
    }

    public InputStream openStream(final String bundle) throws BundleException {
        try {
            return new FileInputStream(new File(bundle));
        } catch (final FileNotFoundException e) {
            throw new BundleException("Unable to open stream for " + bundle, BundleException.UNSPECIFIED, e);
        }
    }

}
