# README

This project's goal is to create a simpler, reuseable OSGi launcher. It should
be useable as a standalone runtime or to launch an embedded runtime inside a
WAR file.

The source code for this project was originally forked from the main subproject
of the Apache Felix SVN repository at revision 1740200.

The initial idea came from
[FELIX-3195](https://issues.apache.org/jira/browse/FELIX-3195) which has still
not been submitted upstream.

The launcher should be able to start an osgi runtime with felix or equinox.

## Configuration

The following configuration properties are for the launcher:

* caffe.auto.deploy.dir - Specifies the auto-deploy directory from which bundles are automatically deployed at framework startup. The default is the bundle/ directory of the current directory.
* caffe.auto.deploy.action - Specifies a comma-delimited list of actions to be performed on bundle JAR files found in the auto-deploy directory. The possible actions are install, update, start, and uninstall. An undefined or blank value is equivalent to disabling auto-deploy processing; there is no default value, so this value must be defined to enable it.
* caffe.auto.install.<n> - Space-delimited list of bundle URLs to automatically install when Felix is started, where <n> is the start level into which the bundle will be installed (e.g., caffe.auto.install.2).
* caffe.auto.start.<n> - Space-delimited list of bundle URLs to automatically install and start when Felix is started, where <n> is the start level into which the bundle will be installed (e.g., caffe.auto.start.2).
* caffe.shutdown.hook - Specifies whether the launcher should install a shutdown hook to cleanly shutdown the framework on process exit. The default value is true.

For configuration your framework, consult its documentation. For felix, the documentation can be found [here](https://felix.apache.org/documentation/subprojects/apache-felix-framework/apache-felix-framework-configuration-properties.html#framework-configuration-properties).

## WAR Deployments

The launcher does not require the servlet container to unpack the war file.
Other components in your runtime may make this assumption, but it is not
required for the launcher code.

More details to come.
