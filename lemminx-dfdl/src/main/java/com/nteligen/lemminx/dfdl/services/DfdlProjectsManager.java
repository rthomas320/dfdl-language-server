package com.nteligen.lemminx.dfdl.Dfdl.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.WorkspaceFolder;

public class DfdlProjectsManager {

    private static final Logger LOGGER = Logger.getLogger(DfdlProjectsManager.class.getName());

    private static final DfdlProjectsManager INSTANCE = new DfdlProjectsManager();

    private List<DfdlWorkspace> DfdlWorkspaceFolders;

    public static DfdlProjectsManager getInstance() {
        return INSTANCE;
    }

    private DfdlProjectsManager() {
        DfdlWorkspaceFolders = new ArrayList<DfdlWorkspace>();
    }

    public void setWorkspaceFolders(List<WorkspaceFolder> workspaceFolders) {
        for (WorkspaceFolder folder : workspaceFolders) {
            DfdlWorkspace DfdlWorkspace = new DfdlWorkspace(folder.getUri());
            this.DfdlWorkspaceFolders.add(DfdlWorkspace);
        }
    }

    public List<DfdlWorkspace> getDfdlWorkspaceFolders() {
        return this.DfdlWorkspaceFolders;
    }

    public String getDfdlVersion(DfdlWorkspace DfdlWorkspace) {
        return DfdlWorkspace.getDfdlVersion();
    }

    /**
     * Given a serverXML URI return the corresponding workspace folder URI
     * 
     * @param serverXMLUri
     * @return
     */
    public DfdlWorkspace getWorkspaceFolder(String serverXMLUri) {
        for (DfdlWorkspace folder : getInstance().getDfdlWorkspaceFolders()) {
            if (serverXMLUri.contains(folder.getURI())) {
                return folder;
            }
        }
        return null;
    }

    public void cleanUpTempDirs() {
        for (DfdlWorkspace folder : getInstance().getDfdlWorkspaceFolders()) {
            // search for Dfdl ls directory
            String workspaceFolderURI = folder.getURI();
            try {
                if (workspaceFolderURI != null) {
                    URI rootURI = new URI(workspaceFolderURI);
                    Path rootPath = Paths.get(rootURI);
                    List<Path> matchingFiles = Files.walk(rootPath)
                            .filter(p -> (Files.isDirectory(p) && p.getFileName().endsWith(".Dfdlls")))
                            .collect(Collectors.toList());

                    // delete each Dfdl ls directory
                    for (Path DfdllsDir : matchingFiles) {
                        if (!DfdllsDir.toFile().delete()) {
                            LOGGER.warning("Could not delete " + DfdllsDir);
                        }
                    }
                }
            } catch (IOException | URISyntaxException e) {
                LOGGER.warning("Could not clean up /.Dfdlls directory: " + e.getMessage());
            }
        }
    }

}
