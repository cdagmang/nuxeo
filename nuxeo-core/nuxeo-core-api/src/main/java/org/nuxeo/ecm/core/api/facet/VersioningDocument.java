/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.ecm.core.api.facet;

/**
 * Declares constants and methods used to control document versions mostly when a document is saved.
 *
 * @author <a href="mailto:dm@nuxeo.com">Dragos Mihalache</a>
 */
public interface VersioningDocument {

    /**
     * @deprecated use {@link VersioningService#VERSIONING_OPTION} instead
     */
    @Deprecated
    String CREATE_SNAPSHOT_ON_SAVE_KEY = "CREATE_SNAPSHOT_ON_SAVE";

    /**
     * @deprecated use {@link VersioningService#VERSIONING_OPTION} instead
     */
    @Deprecated
    String KEY_FOR_INC_OPTION = "VersioningOption";

    /**
     * Key used in options map to send current versions to versioning listener so it will know what version the document
     * had before restoring.
     */
    String CURRENT_DOCUMENT_MINOR_VERSION_KEY = "CURRENT_DOCUMENT_MINOR_VERSION";

    String CURRENT_DOCUMENT_MAJOR_VERSION_KEY = "CURRENT_DOCUMENT_MAJOR_VERSION";

    /**
     * Key used in options map to send the UUID of the version being restored to the listeners.
     */
    String RESTORED_VERSION_UUID_KEY = "RESTORED_VERSION_UUID";

    /**
     * @deprecated use {@link VersioningService#getVersionLabel} instead
     */
    @Deprecated
    Long getMinorVersion();

    /**
     * @deprecated use {@link VersioningService#getVersionLabel} instead
     */
    @Deprecated
    Long getMajorVersion();

    /**
     * Returns a string representation of the version number.
     *
     * @return a string, like {@code "2.1"}
     */
    String getVersionLabel();

}
