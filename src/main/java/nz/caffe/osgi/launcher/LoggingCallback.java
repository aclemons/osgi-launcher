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

/**
 * I really do not want to introduce my own logging abstraction, but most
 * standalone OSGi runtimes will let the bootstrapping of the runtime log errors
 * directly to stderr. When deploying as a WAR file though, it is better to
 * either use SLF4J is that is what the WAR is using or in worst case logging
 * through the ServletContext object.
 */
public interface LoggingCallback
{

    void error(final String message, final Throwable e);

    void warn(final String message, final Throwable e);
}
