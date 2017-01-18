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
package nz.caffe.osgi.launcher;

import org.osgi.framework.launch.Framework;

/**
 * Launchers will implement this interface. After successfully launching the
 * framework, it provides access to the launched framework and possibly its
 * shutdown hook.
 */
public interface Launcher {

    /**
     * If launching was successful, this will return the framework instance.
     *
     * @return the framework the instance
     */
    Framework getFramework();

    /**
     * If launching was successful and the framework configuration included the
     * option to register a shutdown hook, this will return the thread instance.
     *
     * @return the shutdownHook the instance
     */
    Thread getShutdownHook();

    /**
     * Launch the framework.
     *
     * @throws Exception
     *             if launching is unsuccessful
     */
    void launch() throws Exception;
}
