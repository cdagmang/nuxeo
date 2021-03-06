/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     dmetzler
 */
package org.nuxeo.ecm.restapi.jaxrs.io;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.jaxrs.io.JsonHelper;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryEntry;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.restapi.jaxrs.io.directory.DirectoryEntriesWriter;
import org.nuxeo.ecm.restapi.jaxrs.io.directory.DirectoryEntryWriter;
import org.nuxeo.ecm.webengine.JsonFactoryManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @since 5.7.3
 * @deprecated since 7.2 see {@link DirectoryEntriesWriter}
 */
@Deprecated
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy({ "org.nuxeo.ecm.automation.io", "org.nuxeo.ecm.automation.test", "org.nuxeo.ecm.webengine.core" })
@LocalDeploy("org.nuxeo.ecm.automation.test:test-directory-contrib.xml")
public class DirectoryEntryIOTest {

    /**
     *
     */
    private static final String TESTDIRNAME = "testdir";

    @Inject
    DirectoryService ds;

    Session dirSession = null;

    JsonFactoryManager jfm;

    @Before
    public void doBefore() throws Exception {
        dirSession = ds.open(TESTDIRNAME);
    }

    @After
    public void doAfter() throws Exception {
        if (dirSession != null) {
            dirSession.close();
        }
    }

    @Test
    public void itCanWriteADirectoryEntry() throws Exception {

        // Given a directoryEntry
        DocumentModel docEntry = dirSession.getEntry("test1");
        DirectoryEntry entry = new DirectoryEntry(TESTDIRNAME, docEntry);

        // When i write it
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jg = JsonHelper.createJsonGenerator(out);
        DirectoryEntryWriter dew = new DirectoryEntryWriter();
        dew.writeEntity(jg, entry);

        // I can parse it in Json
        ObjectMapper mapper = new ObjectMapper();

        JsonNode node = mapper.readTree(out.toString());

        assertEquals(DirectoryEntryWriter.ENTITY_TYPE, node.get("entity-type").getValueAsText());
        assertEquals(TESTDIRNAME, node.get("directoryName").getValueAsText());
        assertEquals(docEntry.getPropertyValue("vocabulary:label"),
                node.get("properties").get("label").getValueAsText());

    }

    @Test
    public void itCanWriteDirectoryEntries() throws Exception {
        // Given some directory entries
        List<DirectoryEntry> entries = new ArrayList<>();
        for (DocumentModel doc : dirSession.getEntries()) {
            entries.add(new DirectoryEntry(TESTDIRNAME, doc));
        }

        // When i write those entries
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jg = JsonHelper.createJsonGenerator(out);

        DirectoryEntriesWriter writer = new DirectoryEntriesWriter();
        writer.writeEntity(jg, entries);

        // I can parse it in Json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(out.toString());

        assertEquals(DirectoryEntriesWriter.ENTITY_TYPE, node.get("entity-type").getValueAsText());
        ArrayNode jsonEntries = (ArrayNode) node.get("entries");
        assertEquals(entries.size(), jsonEntries.size());

    }
}
