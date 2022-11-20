/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.sdk.index.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Location on disk of a file (e.g. foo.jar, foo.class or foo.java) that contains type(s) in either source or compiled
 * form.
 */
public class CodeLocationDescriptor {
    public CodeLocationIdentifier id; // e.g. org.slf4j:slf4j-api:1.3.4
    public String bazelLabel; // e.g. @maven//:org_slf4j_slf4j_api
    public String version; // optional: contains the version, if available from the filepath (e.g. com/foo/bar/1.3.4/bar.jar)
    public File locationOnDisk;
    public List<ClassIdentifier> containedClasses;
    
    /** 
     * ageInDays - how many days ago was this code artifact built.
     * <p>
     * This is used for reporting functions, for example to look for outdated components being used in a build.
     * <p>
     * This generally should not be the file creation/modification times unless you know your build/source control
     * system preserves the correct data. There may be more authoritative sources for this data.
     * <p>
     * For example, for jar files, we can look at the creation date of the files *inside* the jar.
     * If it cannot be determined, this value will be -1.
     * <p>
     * Note that some build systems (Bazel!) intentionally suppress this information from being written
     * into the jar file for hermeticity reasons, so it is not expected to always be available.
     * <p>
     * Note that in some cases we can reach out to an external system (e.g. Maven Central) and ask for
     * this information. It is up to the indexer implementation to determine if that happens (it is slow!)
     * so not to be used in all cases.
     */
    public int ageInDays = -1;

    public CodeLocationDescriptor() {}
    
    public CodeLocationDescriptor(File locationOnDisk, CodeLocationIdentifier id) {
        this.locationOnDisk = locationOnDisk;
        this.id = id;
    }

    public CodeLocationDescriptor(File locationOnDisk, CodeLocationIdentifier id, String bazelLabel) {
        this.locationOnDisk = locationOnDisk;
        this.id = id;
        this.bazelLabel = bazelLabel;
    }

    public CodeLocationDescriptor(File locationOnDisk, CodeLocationIdentifier id, String bazelLabel, String version) {
        this.locationOnDisk = locationOnDisk;
        this.id = id;
        this.bazelLabel = bazelLabel;
        this.version = version;
    }

    public void addClass(ClassIdentifier classId) {
        if (containedClasses == null) {
            containedClasses = new ArrayList<>(5);
        }
        containedClasses.add(classId);
    }
    
    /**
     * Helper utility used to compute ageInDays, given a valid writtenTimeMillis.
     * <p>
     * @param writtenTimeMillis the candidate time in millis when we think this artifact was created
     * @param currentTimeMillis from System.currentTimeMillis except when running tests
     * @param earliestRealTimestampMillis written time is ignored if less than this time
     * @return true if a reliable age was probably found, false if not
     */
    public boolean computeAge(long writtenTimeMillis, long currentTimeMillis,  long earliestRealTimestampMillis) {
        // writtenTime is computed from epoch (Jan 1 1970) and we can assume anything really old is a bogus
        // date written by a build system that intentionally suppresses the date for hermeticity reasons
        if (writtenTimeMillis < earliestRealTimestampMillis) {
            return false;
        }
        
        long elapsedTimeMillis = currentTimeMillis - writtenTimeMillis;
        if (elapsedTimeMillis < 0) {
            // some bogus future date in the jar, ignore
            return false;
        }
        ageInDays = (int) (elapsedTimeMillis / 86400000); // TODO 24*60*60*1000 be more precise
        
        return true;
    }
    
}
