package com.nteligen.lemminx.dfdl.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import org.eclipse.lemminx.uriresolver.CacheResourcesManager;
import org.eclipse.lemminx.uriresolver.CacheResourcesManager.ResourceToDeploy;

import com.nteligen.lemminx.dfdl.models.feature.Feature;
import com.nteligen.lemminx.dfdl.models.feature.FeatureInfo;
import com.nteligen.lemminx.dfdl.models.feature.WlpInformation;
import com.nteligen.lemminx.dfdl.util.DfdlConstants;
import com.nteligen.lemminx.dfdl.util.DfdlUtils;

public class FeatureService {

  private static final Logger LOGGER = Logger.getLogger(FeatureService.class.getName());

  // Singleton so that only 1 Feature Service can be initialized and is
  // shared between all Lemminx Language Feature Participants

  private static FeatureService instance;

  public static FeatureService getInstance() {
    if (instance == null) {
      instance = new FeatureService();
    }
    return instance;
  }

  // Cache of Dfdl version -> list of supported features
  private Map<String, List<Feature>> featureCache;
  private List<Feature> defaultFeatureList;
  private long featureUpdateTime;

  private FeatureService() {
    featureCache = new HashMap<>();
    featureUpdateTime = -1;
  }

  /**
   * Fetches information about Dfdl features from Maven repo
   *
   * @param DfdlVersion - version of Dfdl to fetch features for
   * @return list of features supported by the provided version of Dfdl
   */
  private List<Feature> fetchFeaturesForVersion(String DfdlVersion) throws IOException, JsonParseException {
    String featureEndpoint = String.format(
        "https://repo1.maven.org/maven2/io/openDfdl/features/features/%s/features-%s.json", DfdlVersion,
        DfdlVersion);
    InputStreamReader reader = new InputStreamReader(new URL(featureEndpoint).openStream());

    // Only need the public features
    ArrayList<Feature> publicFeatures = readPublicFeatures(reader);

    LOGGER.fine("Returning public features from Maven: " + publicFeatures.size());
    return publicFeatures;
  }

  /**
   * Returns the default feature list
   *
   * @return list of features supported by the default version of Dfdl
   */
  private List<Feature> getDefaultFeatureList() {
    try {
      if (defaultFeatureList == null) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("features-20.0.0.9.json");
        InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);

        // Only need the public features
        defaultFeatureList = readPublicFeatures(reader);
      }
      LOGGER.fine("Returning default feature list");
      return defaultFeatureList;

    } catch (JsonParseException e) {
      // unable to read json in resources file, return empty list
      LOGGER.severe("Error: Unable to get default features.");
      return defaultFeatureList;
    }
  }

  /**
   * Returns a list of public features
   *
   * @param reader - InputStreamReader for json feature list
   * @return list of public features
   */
  private ArrayList<Feature> readPublicFeatures(InputStreamReader reader) throws JsonParseException {
    Feature[] featureList = new Gson().fromJson(reader, Feature[].class);

    ArrayList<Feature> publicFeatures = new ArrayList<>();
    Arrays.asList(featureList).stream()
        .filter(f -> f.getWlpInformation().getVisibility().equals(DfdlConstants.PUBLIC_VISIBILITY))
        .forEach(publicFeatures::add);
    defaultFeatureList = publicFeatures;
    return publicFeatures;
  }

  /**
   * Returns the Dfdl features corresponding to the Dfdl version. First
   * attempts to fetch the feature list from Maven, otherwise falls back to the
   * list of installed features. If the installed features list cannot be
   * gathered, falls back to the default feature list.
   * 
   * @param DfdlVersion Dfdl version (corrsponds to XML document)
   * @param requestDelay Time to wait in between feature list requests to Maven
   * @param documentURI Dfdl XML document
   * @return List of possible features
   */
  public List<Feature> getFeatures(String DfdlVersion, int requestDelay, String documentURI) {
    LOGGER.fine("Getting features for version: " + DfdlVersion);
    // if the features are already cached in the feature cache
    if (featureCache.containsKey(DfdlVersion)) {
      return featureCache.get(DfdlVersion);
    }

    // else need to fetch the features from maven central
    try {
      // verify that request delay (seconds) has gone by since last fetch request
      long currentTime = System.currentTimeMillis();
      if (this.featureUpdateTime == -1 || currentTime >= (this.featureUpdateTime + (requestDelay * 1000))) {
        List<Feature> features = fetchFeaturesForVersion(DfdlVersion);
        featureCache.put(DfdlVersion, features);
        this.featureUpdateTime = System.currentTimeMillis();
        return features;
      }
    } catch (Exception e) {
      // do nothing, continue on to returning default feature list
    }

    // fetch installed features list
    List<Feature> installedFeatures = getInstalledFeaturesList(documentURI);
    if (installedFeatures.size() != 0) {
      return installedFeatures;
    }


    // return default feature list
    List<Feature> defaultFeatures = getDefaultFeatureList(); 
    return defaultFeatures;
  }

  public Optional<Feature> getFeature(String featureName, String DfdlVersion, int requestDelay, String documentURI) {
    List<Feature> features = getFeatures(DfdlVersion, requestDelay, documentURI);
    return features.stream().filter(f -> f.getWlpInformation().getShortName().equalsIgnoreCase(featureName))
        .findFirst();
  }

  public boolean featureExists(String featureName, String DfdlVersion, int requestDelay, String documentURI) {
    return this.getFeature(featureName, DfdlVersion, requestDelay, documentURI).isPresent();
  }

  /**
   * Returns the list of installed features generated from ws-featurelist.jar.
   * Generated feature list is stored in the LemMinx cache. Returns an empty list
   * if cannot determine installed feature list.
   * 
   * @param documentURI xml document
   * @return list of installed features, or empty list
   */
  private List<Feature> getInstalledFeaturesList(String documentURI) {
    List<Feature> installedFeatures = new ArrayList<Feature>();
    try {
      DfdlWorkspace DfdlWorkspace = DfdlProjectsManager.getInstance().getWorkspaceFolder(documentURI);
      if (DfdlWorkspace == null || DfdlWorkspace.getURI() == null) {
        return installedFeatures;
      }

      // return installed features from cache
      if (DfdlWorkspace.getInstalledFeatureList().size() != 0) {
        return DfdlWorkspace.getInstalledFeatureList();
      }

      Path featureListJAR = DfdlUtils.findFileInWorkspace(documentURI, "ws-featurelist.jar");

      if (featureListJAR != null && featureListJAR.toFile().exists()) {

        // creating featurelist.xml file in cache
        String XSD_RESOURCE_URL = "https://github.com/OpenDfdl/Dfdl-language-server/blob/master/lemminx-Dfdl/src/main/resources/schema/xsd/Dfdl/featurelist.xml";
        String XSD_CLASSPATH_LOCATION = "/schema/xsd/Dfdl/featurelist.xml";
        ResourceToDeploy FEATURE_LIST_RESOURCE = new ResourceToDeploy(XSD_RESOURCE_URL, XSD_CLASSPATH_LOCATION);
        Path featureListCacheFile = CacheResourcesManager.getResourceCachePath(FEATURE_LIST_RESOURCE);

        if (featureListCacheFile.toFile().exists()) {
          String[] cmd = { "java", "-jar", featureListJAR.toAbsolutePath().toString(),
          featureListCacheFile.toAbsolutePath().toString() };
  
          Process proc = Runtime.getRuntime().exec(cmd);
          BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
          while (in.readLine() != null) {
            // read input from file
          }
  
          JAXBContext jaxbContext = JAXBContext.newInstance(FeatureInfo.class);
          Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
          FeatureInfo featureInfo = (FeatureInfo) jaxbUnmarshaller.unmarshal(featureListCacheFile.toFile());
          if (featureInfo.getFeatures().size() > 0) {
            for (int i = 0; i < featureInfo.getFeatures().size(); i++) {
              Feature f = featureInfo.getFeatures().get(i);
              f.setShortDescription(f.getDescription());
              WlpInformation wlpInfo = new WlpInformation(f.getName());
              f.setWlpInformation(wlpInfo);
            }
            installedFeatures = featureInfo.getFeatures();
            DfdlWorkspace.setInstalledFeatureList(installedFeatures);
          }
        } else {
          LOGGER.warning("Unable to load installed features into LemMinx cache, file does not exist:" + featureListCacheFile.toAbsolutePath());
        }
      }
    } catch (IOException | JAXBException e) {
      LOGGER.warning("Unable to get installed features: " + e);
    }

    LOGGER.fine("Returning installed features: " + installedFeatures.size());
    return installedFeatures;
  }

}
