/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.eclipse.mock;

import java.net.URI;

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

public class MockIProjectDescription implements IProjectDescription {
    private static final String UOE_MSG = "MockIProjectDescription is pay as you go, you have hit a method that is not implemented."; 

    private ICommand[] buildSpec;
    private IProject[] referencedProjects = new IProject[] {};
    private String[] natureIds = new String[] {};
    private IPath location = null;
    private URI locationURI = null;
    
    @Override
    public ICommand[] getBuildSpec() {
        return buildSpec;
    }

    @Override
    public String[] getNatureIds() {
        return this.natureIds;
    }
    
    @Override
    public IPath getLocation() {
        return location;
    }

    @Override
    public URI getLocationURI() {
        return locationURI;
    }

    @Override
    public IProject[] getReferencedProjects() {
        return this.referencedProjects;
    }

    @Override
    public ICommand newCommand() {
        return new MockICommand();
    }

    @Override
    public void setBuildSpec(ICommand[] buildSpec) {
        this.buildSpec = buildSpec;
    }

    
    @Override
    public void setLocation(IPath path) {
        this.location = path;
        if (path != null) {
            this.locationURI = URI.create(path.toOSString());
        } else {
            this.locationURI = null;
        }
    }

    @Override
    public void setLocationURI(URI uri) {            
        this.locationURI = uri;
        if (uri != null) {
            this.location = new Path(uri.getPath());
        } else {
            this.location = null;
        }

    }
    
    @Override
    public void setNatureIds(String[] natures) {
        this.natureIds = natures;
    }

    @Override
    public void setReferencedProjects(IProject[] projects) {
        this.referencedProjects = projects;
    }
    

    
    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.
    

    @Override
    public IBuildConfiguration[] getBuildConfigReferences(String configName) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getComment() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IProject[] getDynamicReferences() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean hasNature(String natureId) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setActiveBuildConfig(String configName) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setBuildConfigs(String[] configNames) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setBuildConfigReferences(String configName, IBuildConfiguration[] references) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setComment(String comment) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDynamicReferences(IProject[] projects) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setName(String projectName) {
        throw new UnsupportedOperationException(UOE_MSG);
    }


}
