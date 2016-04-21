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

## WAR Deployments

The launcher does not require the servlet container to unpack the war file.
Other components in your runtime may make this assumption, but it is not
required for the launcher code.

More details to come.