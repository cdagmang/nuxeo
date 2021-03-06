/*
 * (C) Copyright 2006-2009 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Damien Metzler (Leroy Merlin, http://www.leroymerlin.fr/)
 */
package org.nuxeo.ecm.webengine.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.test.JettyTransactionalFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.webengine.jaxrs.session.SessionFactory;
import org.nuxeo.runtime.test.WorkingDirectoryConfigurator;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.RuntimeHarness;
import org.nuxeo.runtime.test.runner.SimpleFeature;
import org.nuxeo.runtime.test.runner.web.WebDriverFeature;

@Deploy({ "org.nuxeo.ecm.platform.login", "org.nuxeo.ecm.platform.login.default", "org.nuxeo.ecm.webengine.admin",
        "org.nuxeo.ecm.webengine.jaxrs", "org.nuxeo.ecm.webengine.base", "org.nuxeo.ecm.webengine.ui",
        "org.nuxeo.ecm.webengine.gwt", "org.nuxeo.ecm.platform.test:test-usermanagerimpl/userservice-config.xml",
        "org.nuxeo.ecm.webengine.test:login-anonymous-config.xml", "org.nuxeo.ecm.webengine.test:login-config.xml",
        "org.nuxeo.ecm.webengine.test:runtimeserver-contrib.xml", "org.nuxeo.ecm.core.io" })
@Features({ PlatformFeature.class, WebDriverFeature.class, JettyTransactionalFeature.class, WebEngineFeatureCore.class })
public class WebEngineFeature extends SimpleFeature implements WorkingDirectoryConfigurator {

    protected URL config;

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        WebXml anno = FeaturesRunner.getScanner().getFirstAnnotation(runner.getTargetTestClass(), WebXml.class);
        if (anno == null) {
            config = getResource("webengine/web/WEB-INF/web.xml");
        } else {
            config = runner.getTargetTestClass().getClassLoader().getResource(anno.value());
        }
        runner.getFeature(RuntimeFeature.class).getHarness().addWorkingDirectoryConfigurator(this);
    }

    @Override
    public void configure(RuntimeHarness harness, File workingDir) throws IOException {
        SessionFactory.setDefaultRepository("test");
        File dest = new File(workingDir, "web/root.war/WEB-INF/");
        dest.mkdirs();

        if (config == null) {
            throw new java.lang.IllegalStateException("No custom web.xml was found. "
                    + "Check your @WebXml annotation on the test class");
        }
        InputStream in = config.openStream();
        dest = new File(workingDir + "/web/root.war/WEB-INF/", "web.xml");
        try {
            FileUtils.copyToFile(in, dest);
        } finally {
            in.close();
        }
    }

    private static URL getResource(String resource) {
        return Thread.currentThread().getContextClassLoader().getResource(resource);
    }

    // public void deployTestModule() {
    // URL currentDir =
    // Thread.currentThread().getContextClassLoader().getResource(
    // ".");
    // ModuleManager moduleManager =
    // Framework.getLocalService(WebEngine.class).getModuleManager();
    // moduleManager.loadModuleFromDir(new File(currentDir.getFile()));
    // }
}
