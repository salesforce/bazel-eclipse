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
