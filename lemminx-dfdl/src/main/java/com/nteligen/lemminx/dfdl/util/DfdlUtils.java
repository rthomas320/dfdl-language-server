package com.nteligen.lemminx.dfdl.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.lemminx.dom.DOMDocument;

import com.nteligen.lemminx.dfdl.models.feature.Feature;
import com.nteligen.lemminx.dfdl.services.DfdlProjectsManager;
import com.nteligen.lemminx.dfdl.services.DfdlWorkspace;
import com.nteligen.lemminx.dfdl.services.SettingsService;

public class DfdlUtils {

    private static final Logger LOGGER = Logger.getLogger(DfdlUtils.class.getName());

    private static Thread thread;

    private DfdlUtils() {
    }

    public static boolean isServerXMLFile(String filePath) {
        return filePath.endsWith("/" + DfdlConstants.SERVER_XML);
    }

    public static boolean isServerXMLFile(DOMDocument file) {
        return file.getDocumentURI().endsWith("/" + DfdlConstants.SERVER_XML);
    }

    /**
     * Given a server.xml URI find the associated workspace folder and search that
     * folder for the most recently edited file that matches the given name.
     * 
     * @param serverXmlURI
     * @param filename
     * @return path to given file or null if could not be found
     */
    public static Path findFileInWorkspace(String serverXmlURI, String filename) {
        DfdlWorkspace DfdlWorkspace = DfdlProjectsManager.getInstance().getWorkspaceFolder(serverXmlURI);
        if (DfdlWorkspace.getURI() == null) {
            return null;
        }
        try {
            URI rootURI = new URI(DfdlWorkspace.getURI());
            Path rootPath = Paths.get(rootURI);
            List<Path> matchingFiles = Files.walk(rootPath)
                    .filter(p -> (Files.isRegularFile(p) && p.getFileName().endsWith(filename)))
                    .collect(Collectors.toList());
            if (matchingFiles.isEmpty()) {
                return null;
            }
            if (matchingFiles.size() == 1) {
                return matchingFiles.get(0);
            }
            Path lastModified = matchingFiles.get(0);
            for (Path p : matchingFiles) {
                if (lastModified.toFile().lastModified() < p.toFile().lastModified()) {
                    lastModified = p;
                }
            }
            return lastModified;
        } catch (IOException | URISyntaxException e) {
            LOGGER.warning("Could not find: " + filename + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Given a server.xml find the version associated with the corresponding Dfdl
     * workspace. If the version has not been set via the Settings Service, search for a
     * Dfdl.properties file in the workspace and return the version from that
     * file. Otherwise, return null.
     * 
     * @param serverXML server xml associated
     * @return version of Dfdl or null
     */
    public static String getVersion(DOMDocument serverXML) {
        // return version set in settings if it exists
        String DfdlVersion = SettingsService.getInstance().getDfdlVersion();
        if (DfdlVersion != null) {
            return DfdlVersion;
        }
        // find workspace folder this serverXML belongs to
        DfdlWorkspace DfdlWorkspace = DfdlProjectsManager.getInstance().getWorkspaceFolder(serverXML.getDocumentURI());

        if (DfdlWorkspace == null || DfdlWorkspace.getURI() == null) {
            return null;
        }

        String version = DfdlWorkspace.getDfdlVersion();

        // return version from cache if set and Dfdl is installed
        if (version != null && DfdlWorkspace.isDfdlInstalled()) {
            return version;
        }
        Path propertiesFile = findFileInWorkspace(serverXML.getDocumentURI(), "openDfdl.properties");

        // detected a new Dfdl properties file, re-calculate version
        if (propertiesFile != null && propertiesFile.toFile().exists()) {
            // new properties file, reset the installed features stored in the feature cache
            // so that the installed features list will be regenerated as it may have
            // changed between Dfdl installations
            DfdlWorkspace.setInstalledFeatureList(new ArrayList<Feature>());
            Properties prop = new Properties();
            try {
                // add a file watcher on this file
                if (!DfdlWorkspace.isDfdlInstalled()) {
                    watchFiles(propertiesFile, DfdlWorkspace);
                }

                FileInputStream fis = new FileInputStream(propertiesFile.toFile());
                prop.load(fis);
                version = prop.getProperty("com.ibm.websphere.productVersion");
                DfdlWorkspace.setDfdlVersion(version);
                DfdlWorkspace.setDfdlInstalled(true);
                return version;
            } catch (IOException e) {
                LOGGER.warning("Unable to get version from properties file: " + propertiesFile.toString() + ": "
                        + e.getMessage());
                return null;
            }
        } else {
            // did not detect a new Dfdl properties file, return version from cache
            return version;
        }
    }

    // /**
    //  * Return temp directory to store generated feature lists and schema. Creates
    //  * temp directory if it does not exist.
    //  * 
    //  * @param folder WorkspaceFolderURI indicates where to create the temporary
    //  *               directory
    //  * @return temporary directory File object
    //  */
    // public static File getTempDir(String workspaceFolderURI) {
    //     if (workspaceFolderURI == null) {
    //         return null;
    //     }
    //     try {
    //         URI rootURI = new URI(workspaceFolderURI);
    //         Path rootPath = Paths.get(rootURI);
    //         File file = rootPath.toFile();
    //         File DfdlLSFolder = new File(file, ".Dfdlls");

    //         if (!DfdlLSFolder.exists()) {
    //             if (!DfdlLSFolder.mkdir()) {
    //                 return null;
    //             }
    //         }
    //         return file;
    //     } catch (Exception e) {
    //         LOGGER.warning("Unable to create temp dir: " + e.getMessage());
    //     }
    //     return null;
    // }

    /**
     * Watches the parent directory of the Dfdl properties file in a separate
     * thread. If the the contents of the directory have been modified or deleted,
     * the installation of Dfdl has changed and the corresponding Dfdl
     * Workspace item is updated.
     * 
     * @param propertiesFile   openDfdl.properties file to watch
     * @param DfdlWorkspace Dfdl Workspace object, updated to indicate if
     *                         there is an associated installation of Dfdl
     */
    public static void watchFiles(Path propertiesFile, DfdlWorkspace DfdlWorkspace) {
        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            propertiesFile.getParent().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            thread = new Thread(() -> {
                WatchKey watchKey = null;
                try {
                    while (true) {
                        watchKey = watcher.poll(5, TimeUnit.SECONDS);
                        if (watchKey != null) {
                            watchKey.pollEvents().stream().forEach(event -> {
                                LOGGER.fine("Dfdl properties file (" + propertiesFile + ") has been modified: "
                                        + event.context());
                                // if modified re-calculate version
                                DfdlWorkspace.setDfdlInstalled(false);
                            });

                            // if watchkey.reset() returns false indicates that the parent folder has been
                            // deleted
                            boolean valid = watchKey.reset();
                            if (!valid) {
                                // if deleted re-calculate version
                                LOGGER.fine("Dfdl properties file (" + propertiesFile + ") has been deleted");
                                DfdlWorkspace.setDfdlInstalled(false);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    LOGGER.warning("Unable to watch properties file(s): " + e.toString());
                }
            });
            thread.start();
        } catch (IOException e) {
            LOGGER.warning("Unable to watch properties file(s): " + e.toString());
        }
    }

}
