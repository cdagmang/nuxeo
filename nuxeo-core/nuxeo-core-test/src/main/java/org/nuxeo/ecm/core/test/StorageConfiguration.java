/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.DEFAULT_MARKLOGIC_DBNAME;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.DEFAULT_MARKLOGIC_HOST;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.DEFAULT_MARKLOGIC_PASSWORD;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.DEFAULT_MARKLOGIC_PORT;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.DEFAULT_MARKLOGIC_USER;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.MARKLOGIC_DBNAME_PROPERTY;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.MARKLOGIC_HOST_PROPERTY;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.MARKLOGIC_PASSWORD_PROPERTY;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.MARKLOGIC_PORT_PROPERTY;
import static org.nuxeo.ecm.core.test.MarkLogicConstants.MARKLOGIC_USER_PROPERTY;
import static org.nuxeo.ecm.core.test.MongoDBConstants.DEFAULT_MONGODB_DBNAME;
import static org.nuxeo.ecm.core.test.MongoDBConstants.DEFAULT_MONGODB_SERVER;
import static org.nuxeo.ecm.core.test.MongoDBConstants.MONGODB_DBNAME_PROPERTY;
import static org.nuxeo.ecm.core.test.MongoDBConstants.MONGODB_SERVER_PROPERTY;

import java.net.URL;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.function.BiConsumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.storage.marklogic.MarkLogicRepository;
import org.nuxeo.ecm.core.storage.marklogic.MarkLogicRepositoryDescriptor;
import org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository;
import org.nuxeo.ecm.core.storage.mongodb.MongoDBRepositoryDescriptor;
import org.nuxeo.ecm.core.storage.sql.DatabaseDB2;
import org.nuxeo.ecm.core.storage.sql.DatabaseDerby;
import org.nuxeo.ecm.core.storage.sql.DatabaseH2;
import org.nuxeo.ecm.core.storage.sql.DatabaseHelper;
import org.nuxeo.ecm.core.storage.sql.DatabaseMySQL;
import org.nuxeo.ecm.core.storage.sql.DatabaseOracle;
import org.nuxeo.ecm.core.storage.sql.DatabasePostgreSQL;
import org.nuxeo.ecm.core.storage.sql.DatabaseSQLServer;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.RuntimeHarness;
import org.osgi.framework.Bundle;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.query.QueryManager;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;

/**
 * Description of the specific capabilities of a repository for tests, and helper methods.
 *
 * @since 7.3
 */
public class StorageConfiguration {

    private static final Log log = LogFactory.getLog(StorageConfiguration.class);

    public static final String CORE_PROPERTY = "nuxeo.test.core";

    public static final String CORE_VCS = "vcs";

    public static final String CORE_MEM = "mem";

    public static final String CORE_MONGODB = "mongodb";

    public static final String CORE_MARKLOGIC = "marklogic";

    public static final String DEFAULT_CORE = CORE_VCS;

    private String coreType;

    private boolean isVCS;

    private boolean isDBS;

    private DatabaseHelper databaseHelper;

    final CoreFeature feature;

    public StorageConfiguration(CoreFeature feature) {
        coreType = defaultSystemProperty(CORE_PROPERTY, DEFAULT_CORE);
        this.feature = feature;
    }

    protected static String defaultSystemProperty(String name, String def) {
        String value = System.getProperty(name);
        if (value == null || value.equals("") || value.equals("${" + name + "}")) {
            System.setProperty(name, value = def);
        }
        return value;
    }

    protected static String defaultProperty(String name, String def) {
        String value = System.getProperty(name);
        if (value == null || value.equals("") || value.equals("${" + name + "}")) {
            value = def;
        }
        Framework.getProperties().setProperty(name, value);
        return value;
    }

    protected void init() {
        initJDBC();
        switch (coreType) {
        case CORE_VCS:
            isVCS = true;
            break;
        case CORE_MEM:
            isDBS = true;
            break;
        case CORE_MONGODB:
            isDBS = true;
            initMongoDB();
            break;
        case CORE_MARKLOGIC:
            isDBS = true;
            initMarkLogic();
            break;
        default:
            throw new ExceptionInInitializerError("Unknown test core mode: " + coreType);
        }
    }

    protected void initJDBC() {
        databaseHelper = DatabaseHelper.DATABASE;

        String msg = "Deploying JDBC using " + databaseHelper.getClass().getSimpleName();
        // System.out used on purpose, don't remove
        System.out.println(getClass().getSimpleName() + ": " + msg);
        log.info(msg);

        // setup system properties for generic XML extension points
        // this is used both for VCS (org.nuxeo.ecm.core.storage.sql.RepositoryService)
        // and DataSources (org.nuxeo.runtime.datasource) extension points
        try {
            databaseHelper.setUp();
        } catch (SQLException e) {
            throw new NuxeoException(e);
        }
    }

    protected void initMongoDB() {
        String server = defaultProperty(MONGODB_SERVER_PROPERTY, DEFAULT_MONGODB_SERVER);
        String dbname = defaultProperty(MONGODB_DBNAME_PROPERTY, DEFAULT_MONGODB_DBNAME);
        MongoDBRepositoryDescriptor descriptor = new MongoDBRepositoryDescriptor();
        descriptor.name = getRepositoryName();
        descriptor.server = server;
        descriptor.dbname = dbname;
        try {
            clearMongoDB(descriptor);
        } catch (UnknownHostException e) {
            throw new NuxeoException(e);
        }
    }

    protected void clearMongoDB(MongoDBRepositoryDescriptor descriptor) throws UnknownHostException {
        MongoClient mongoClient = MongoDBRepository.newMongoClient(descriptor);
        try {
            DBCollection coll = MongoDBRepository.getCollection(descriptor, mongoClient);
            coll.dropIndexes();
            coll.remove(new BasicDBObject());
            coll = MongoDBRepository.getCountersCollection(descriptor, mongoClient);
            coll.dropIndexes();
            coll.remove(new BasicDBObject());
        } finally {
            mongoClient.close();
        }
    }

    protected void initMarkLogic() {
        String host = defaultProperty(MARKLOGIC_HOST_PROPERTY, DEFAULT_MARKLOGIC_HOST);
        int port = Integer.parseInt(defaultProperty(MARKLOGIC_PORT_PROPERTY, DEFAULT_MARKLOGIC_PORT));
        String dbname = defaultProperty(MARKLOGIC_DBNAME_PROPERTY, DEFAULT_MARKLOGIC_DBNAME);
        String user = defaultProperty(MARKLOGIC_USER_PROPERTY, DEFAULT_MARKLOGIC_USER);
        String password = defaultProperty(MARKLOGIC_PASSWORD_PROPERTY, DEFAULT_MARKLOGIC_PASSWORD);
        MarkLogicRepositoryDescriptor descriptor = new MarkLogicRepositoryDescriptor();
        descriptor.name = getRepositoryName();
        descriptor.host = host;
        descriptor.port = port;
        descriptor.dbname = dbname;
        descriptor.user = user;
        descriptor.password = password;

        clearMarkLogic(descriptor);
    }

    protected void clearMarkLogic(MarkLogicRepositoryDescriptor descriptor) {
        DatabaseClient markLogicClient = MarkLogicRepository.newMarkLogicClient(descriptor);
        QueryManager queryManager = markLogicClient.newQueryManager();
        queryManager.delete(queryManager.newDeleteDefinition());
        markLogicClient.release();
    }

    public boolean isVCS() {
        return isVCS;
    }

    public boolean isVCSH2() {
        return isVCS && databaseHelper instanceof DatabaseH2;
    }

    public boolean isVCSDerby() {
        return isVCS && databaseHelper instanceof DatabaseDerby;
    }

    public boolean isVCSPostgreSQL() {
        return isVCS && databaseHelper instanceof DatabasePostgreSQL;
    }

    public boolean isVCSMySQL() {
        return isVCS && databaseHelper instanceof DatabaseMySQL;
    }

    public boolean isVCSOracle() {
        return isVCS && databaseHelper instanceof DatabaseOracle;
    }

    public boolean isVCSSQLServer() {
        return isVCS && databaseHelper instanceof DatabaseSQLServer;
    }

    public boolean isVCSDB2() {
        return isVCS && databaseHelper instanceof DatabaseDB2;
    }

    public boolean isDBS() {
        return isDBS;
    }

    public boolean isDBSMem() {
        return isDBS && CORE_MEM.equals(coreType);
    }

    public boolean isDBSMongoDB() {
        return isDBS && CORE_MONGODB.equals(coreType);
    }

    public boolean isDBSMarkLogic() {
        return isDBS && CORE_MARKLOGIC.equals(coreType);
    }

    public String getRepositoryName() {
        return "test";
    }

    /**
     * For databases that do asynchronous fulltext indexing, sleep a bit.
     */
    public void sleepForFulltext() {
        if (isVCS()) {
            databaseHelper.sleepForFulltext();
        } else {
            // DBS
        }
    }

    /**
     * For databases that don't have sub-second resolution, sleep a bit to get to the next second.
     */
    public void maybeSleepToNextSecond() {
        if (isVCS()) {
            databaseHelper.maybeSleepToNextSecond();
        } else {
            // DBS
        }
        // sleep 1 ms nevertheless to have different timestamps
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupted status
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks if the database has sub-second resolution.
     */
    public boolean hasSubSecondResolution() {
        if (isVCS()) {
            return databaseHelper.hasSubSecondResolution();
        } else {
            return true; // DBS
        }
    }

    public void waitForAsyncCompletion() {
        feature.waitForAsyncCompletion();
    }

    public void waitForFulltextIndexing() {
        waitForAsyncCompletion();
        sleepForFulltext();
    }

    /**
     * Checks if the database supports multiple fulltext indexes.
     */
    public boolean supportsMultipleFulltextIndexes() {
        if (isVCS()) {
            return databaseHelper.supportsMultipleFulltextIndexes();
        } else {
            return false; // DBS
        }
    }

    public URL getBlobManagerContrib(FeaturesRunner runner) {
        String bundleName = "org.nuxeo.ecm.core.test";
        String contribPath = "OSGI-INF/test-storage-blob-contrib.xml";
        RuntimeHarness harness = runner.getFeature(RuntimeFeature.class).getHarness();
        Bundle bundle = harness.getOSGiAdapter().getRegistry().getBundle(bundleName);
        URL contribURL = bundle.getEntry(contribPath);
        assertNotNull("deployment contrib " + contribPath + " not found", contribURL);
        return contribURL;
    }

    public URL getRepositoryContrib(FeaturesRunner runner) {
        String msg;
        if (isVCS()) {
            msg = "Deploying a VCS repository";
        } else if (isDBS()) {
            msg = "Deploying a DBS repository using " + coreType;
        } else {
            throw new NuxeoException("Unkown test configuration (not vcs/dbs)");
        }
        // System.out used on purpose, don't remove
        System.out.println(getClass().getSimpleName() + ": " + msg);
        log.info(msg);

        String contribPath;
        String bundleName;
        if (isVCS()) {
            bundleName = "org.nuxeo.ecm.core.storage.sql.test";
            contribPath = databaseHelper.getDeploymentContrib();
        } else {
            bundleName = "org.nuxeo.ecm.core.test";
            if (isDBSMem()) {
                contribPath = "OSGI-INF/test-storage-repo-mem-contrib.xml";
            } else if (isDBSMongoDB()) {
                contribPath = "OSGI-INF/test-storage-repo-mongodb-contrib.xml";
            } else if (isDBSMarkLogic()) {
                contribPath = "OSGI-INF/test-storage-repo-marklogic-contrib.xml";
            } else {
                throw new NuxeoException("Unkown DBS test configuration (not mem/mongodb/marklogic/...)");
            }
        }
        RuntimeHarness harness = runner.getFeature(RuntimeFeature.class).getHarness();
        Bundle bundle = harness.getOSGiAdapter().getRegistry().getBundle(bundleName);
        URL contribURL = bundle.getEntry(contribPath);
        assertNotNull("deployment contrib " + contribPath + " not found", contribURL);
        return contribURL;
    }

    public void assertEqualsTimestamp(Calendar expected, Calendar actual) {
        assertEquals(convertToStoredCalendar(expected), convertToStoredCalendar(actual));
    }

    public void assertNotEqualsTimestamp(Calendar expected, Calendar actual) {
        assertNotEquals(convertToStoredCalendar(expected), convertToStoredCalendar(actual));
    }

    /**
     * Due to some DB restriction this method could fire a false negative. For example 1001ms is before 1002ms but it's
     * not the case for MySQL (they're equals).
     */
    public void assertBeforeTimestamp(Calendar expected, Calendar actual) {
        BiConsumer<Calendar, Calendar> assertTrue = (exp, act) -> assertTrue(
                String.format("expected=%s is not before actual=%s", exp, act), exp.before(act));
        assertTrue.accept(convertToStoredCalendar(expected), convertToStoredCalendar(actual));
    }

    public void assertNotBeforeTimestamp(Calendar expected, Calendar actual) {
        BiConsumer<Calendar, Calendar> assertFalse = (exp, act) -> assertFalse(
                String.format("expected=%s is before actual=%s", exp, act), exp.before(act));
        assertFalse.accept(convertToStoredCalendar(expected), convertToStoredCalendar(actual));
    }

    /**
     * Due to some DB restriction this method could fire a false negative. For example 1002ms is after 1001ms but it's
     * not the case for MySQL (they're equals).
     */
    public void assertAfterTimestamp(Calendar expected, Calendar actual) {
        BiConsumer<Calendar, Calendar> assertTrue = (exp, act) -> assertTrue(
                String.format("expected=%s is not after actual=%s", exp, act), exp.after(act));
        assertTrue.accept(convertToStoredCalendar(expected), convertToStoredCalendar(actual));
    }

    public void assertNotAfterTimestamp(Calendar expected, Calendar actual) {
        BiConsumer<Calendar, Calendar> assertFalse = (exp, act) -> assertFalse(
                String.format("expected=%s is after actual=%s", exp, act), exp.after(act));
        assertFalse.accept(convertToStoredCalendar(expected), convertToStoredCalendar(actual));
    }

    private Calendar convertToStoredCalendar(Calendar calendar) {
        if (isVCSMySQL() || isVCSSQLServer()) {
            Calendar result = (Calendar) calendar.clone();
            result.setTimeInMillis(convertToStoredTimestamp(result.getTimeInMillis()));
            return result;
        }
        return calendar;
    }

    private long convertToStoredTimestamp(long timestamp) {
        if (isVCSMySQL()) {
            return timestamp / 1000 * 1000;
        } else if (isVCSSQLServer()) {
            // as datetime in SQL Server are rounded to increments of .000, .003, or .007 seconds
            // see https://msdn.microsoft.com/en-us/library/aa258277(SQL.80).aspx
            long milliseconds = timestamp % 10;
            long newTimestamp = timestamp - milliseconds;
            if (milliseconds == 9) {
                newTimestamp += 10;
            } else if (milliseconds >= 5) {
                newTimestamp += 7;
            } else if (milliseconds >= 2) {
                newTimestamp += 3;
            }
            return newTimestamp;
        }
        return timestamp;
    }

}
