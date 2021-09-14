package com.nteligen.lemminx.dfdl.services;

import java.util.ArrayList;
import java.util.List;

import com.nteligen.lemminx.dfdl.models.feature.Feature;

public class DfdlWorkspace {

    private String workspaceFolderURI;
    private String DfdlVersion;
    private boolean isDfdlInstalled;
    private List<Feature> installedFeatureList;

    /**
     * Model of a Dfdl Workspace. Each workspace indicates the
     * workspaceFolderURI, the Dfdl version associated (may be cached), and if an
     * installed Dfdl instance has been detected.
     * 
     * @param workspaceFolderURI
     */
    public DfdlWorkspace(String workspaceFolderURI) {
        this.workspaceFolderURI = workspaceFolderURI;
        this.DfdlVersion = null;
        this.isDfdlInstalled = false;
        this.installedFeatureList = new ArrayList<Feature>();
    }

    public String getURI() {
        return this.workspaceFolderURI;
    }

    public void setDfdlVersion(String DfdlVersion) {
        this.DfdlVersion = DfdlVersion;
    }

    public String getDfdlVersion() {
        return this.DfdlVersion;
    }

    public void setDfdlInstalled(boolean isDfdlInstalled) {
        this.isDfdlInstalled = isDfdlInstalled;
    }

    public boolean isDfdlInstalled() {
        return this.isDfdlInstalled;
    }

    public List<Feature> getInstalledFeatureList() {
        return this.installedFeatureList;
    }

    public void setInstalledFeatureList(List<Feature> installedFeatureList){
        this.installedFeatureList = installedFeatureList;
    }

}
