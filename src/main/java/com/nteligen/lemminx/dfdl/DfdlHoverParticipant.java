package com.nteligen.lemminx.dfdl;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.services.extensions.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.IHoverRequest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import com.nteligen.lemminx.dfdl.models.feature.*;
import com.nteligen.lemminx.dfdl.services.FeatureService;
import com.nteligen.lemminx.dfdl.services.SettingsService;
import com.nteligen.lemminx.dfdl.Dfdl.util.*;

import java.util.Optional;

public class DfdlHoverParticipant implements IHoverParticipant {

	@Override
	public Hover onAttributeName(IHoverRequest request) {
		return null;
	}

	@Override
	public Hover onAttributeValue(IHoverRequest request) {
		return null;
	}

	@Override
	public Hover onTag(IHoverRequest request) {
		return null;
	}

	@Override
	public Hover onText(IHoverRequest request) {
		if (!DfdlUtils.isServerXMLFile(request.getXMLDocument()))
			return null;

		DOMElement parentElement = request.getParentElement();
		if (parentElement == null || parentElement.getTagName() == null)
			return null;

		// if we are hovering over text inside a <feature> element
		if (DfdlConstants.FEATURE_ELEMENT.equals(parentElement.getTagName())) {
			String featureName = request.getNode().getTextContent();
			return getHoverFeatureDescription(featureName, request.getXMLDocument());
		}

		return null;
	}

	private Hover getHoverFeatureDescription(String featureName, DOMDocument domDocument) {
		String DfdlVersion = DfdlUtils.getVersion(domDocument);

		final int requestDelay = SettingsService.getInstance().getRequestDelay();
		Optional<Feature> feature = FeatureService.getInstance().getFeature(featureName, DfdlVersion, requestDelay, domDocument.getDocumentURI());
		if (feature.isPresent()) {
			return new Hover(new MarkupContent("plaintext", feature.get().getShortDescription()));
		}

		return null;
	}
}