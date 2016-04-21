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

import java.io.InputStream;
import java.util.List;

import org.osgi.framework.BundleException;

/**
 * Abstraction for accessing the location of bundles. These may not actually be
 * on the file system as files. They could be embedded inside the WAR file.
 */
public interface LoadCallback {

    /**
     * Returns the bundles found in the given directory.
     *
     * @param directory
     *            the directory to search.
     * @return the list of bundles
     */
    List<String> listBundles(final String directory);

    /**
     * Open a stream to the given path.
     *
     * @param path
     *            the path.
     * @return the input stream - the caller is responsible for closing.
     * @throws BundleException
     *             if opening the stream fails
     */
    InputStream openStream(final String path) throws BundleException;

}
