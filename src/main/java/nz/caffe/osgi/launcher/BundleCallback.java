/**
 *
 */
package nz.caffe.osgi.launcher;

import java.io.InputStream;
import java.util.List;

import org.osgi.framework.BundleException;

/**
 * Abstraction for accessing the location of bundles. These may not actually be
 * on the file system as files. They could be embedded inside the WAR file.
 */
public interface BundleCallback
{

    /**
     * Returns the bundles found in the given directory.
     *
     * @param directory
     *            the directory to search.
     * @return the list of bundles
     */
    List<String> listBundles(final String directory);

    /**
     * Open a stream to the given bundle.
     *
     * @param bundle
     *            the bundle.
     * @return the input stream - the caller is responsible for closing.
     * @throws BundleException if opening the stream fails
     */
    InputStream openStream(final String bundle) throws BundleException;

}
