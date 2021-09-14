package com.nteligen.lemminx.dfdl;

import org.eclipse.lemminx.services.extensions.ICompletionParticipant;
import org.eclipse.lemminx.services.extensions.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lemminx.services.extensions.save.ISaveContext.SaveContextType;
import org.eclipse.lemminx.uriresolver.URIResolverExtension;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceFolder;

import java.util.List;
import java.util.logging.Logger;

import com.nteligen.lemminx.dfdl.services.DfdlProjectsManager;
import com.nteligen.lemminx.dfdl.services.SettingsService;

public class DfdlExtension implements IXMLExtension {

    private static final Logger LOGGER = Logger.getLogger(DfdlExtension.class.getName());

    private URIResolverExtension xsdResolver;
    private ICompletionParticipant completionParticipant;
    private IHoverParticipant hoverParticipant;
    private IDiagnosticsParticipant diagnosticsParticipant;

    @Override
    public void start(InitializeParams initializeParams, XMLExtensionsRegistry xmlExtensionsRegistry) {
        try {
            List<WorkspaceFolder> folders = initializeParams.getWorkspaceFolders();
            if (folders != null) {
                DfdlProjectsManager.getInstance().setWorkspaceFolders(folders);
            }
        } catch (NullPointerException e) {
            LOGGER.warning("Could not get workspace folders: " + e.toString());
        }
        xsdResolver = new DfdlXSDURIResolver();
        xmlExtensionsRegistry.getResolverExtensionManager().registerResolver(xsdResolver);

        completionParticipant = new DfdlCompletionParticipant();
        xmlExtensionsRegistry.registerCompletionParticipant(completionParticipant);

        hoverParticipant = new DfdlHoverParticipant();
        xmlExtensionsRegistry.registerHoverParticipant(hoverParticipant);

        diagnosticsParticipant = new DfdlDiagnosticParticipant();
        xmlExtensionsRegistry.registerDiagnosticsParticipant(diagnosticsParticipant);
    }

    @Override
    public void stop(XMLExtensionsRegistry xmlExtensionsRegistry) {
        // clean up .Dfdlls folders
        DfdlProjectsManager.getInstance().cleanUpTempDirs();

        xmlExtensionsRegistry.getResolverExtensionManager().unregisterResolver(xsdResolver);
        xmlExtensionsRegistry.unregisterCompletionParticipant(completionParticipant);
        xmlExtensionsRegistry.unregisterHoverParticipant(hoverParticipant);
        xmlExtensionsRegistry.unregisterDiagnosticsParticipant(diagnosticsParticipant);
    }

    // Do save is called on startup with a Settings update
    // and any time the settings are updated.
    @Override
    public void doSave(ISaveContext saveContext) {
        // Only need to update settings if the save event was for settings
        // Not if an xml file was updated.
        if (saveContext.getType() == SaveContextType.SETTINGS) {
            Object xmlSettings = saveContext.getSettings();
            SettingsService.getInstance().updateDfdlSettings(xmlSettings);
            LOGGER.fine("Dfdl XML settings updated");
        }
    }
}
