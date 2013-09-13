/*
 * Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.services.samples.analytics.cmdline;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.analytics.Analytics;
import com.google.api.services.analytics.AnalyticsScopes;
import com.google.api.services.analytics.model.Accounts;
import com.google.api.services.analytics.model.GaData;
import com.google.api.services.analytics.model.GaData.ColumnHeaders;
import com.google.api.services.analytics.model.GaData.Query;
import com.google.api.services.analytics.model.Profiles;
import com.google.api.services.analytics.model.Webproperties;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;


/**
 * This is a basic hello world sample for the Google Analytics API. It is designed to run from the
 * command line and will prompt a user to grant access to their data. Once complete, the sample will
 * traverse the Management API hierarchy by going through the authorized user's first account, first
 * web property, and finally the first profile and retrieve the first profile id. This ID is then
 * used with the Core Reporting API to retrieve the top 25 organic search terms.
 *
 * @author
 */
public class HelloAnalyticsApiSample {

  /**
   * Be sure to specify the name of your application. If the application name is {@code null} or
   * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
   */
  private static final String APPLICATION_NAME = "Thesis";

  /** Directory to store user credentials. */
  private static final java.io.File DATA_STORE_DIR =
      new java.io.File(System.getProperty("user.home"), ".store/analytics_sample");
  
  /**
   * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
   * globally shared instance across your application.
   */
  private static FileDataStoreFactory DATA_STORE_FACTORY;

  /** Global instance of the HTTP transport. */
  private static HttpTransport HTTP_TRANSPORT;

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    
  /**Maximum of 10 metrics per request */
  private static final String metrics_table1 = "ga:visits,ga:visitors,ga:timeOnPage,ga:pageviews,ga:bounces";
  /**Maximum of 7 dimensions per request */
  private static final String dimensions_table1 = "ga:pagepath,ga:date,ga:visitlength";

  /**Maximum of 10 metrics per request */
  private static final String metrics_table2 = "ga:visits, ga:visitors, ga:pageviews, ga:uniquepageviews";
  /**Maximum of 7 dimensions per request */
  private static final String dimensions_table2 = "ga:date, ga:landingpagepath, ga:secondpagepath, ga:exitpagepath";
  
  /**Maximum of 10 metrics per request */
  private static final String metrics_table3 = "ga:visits, ga:visitors, ga:pageviews, ga:bounces";
  /**Maximum of 7 dimensions per request */
  private static final String dimensions_table3 = "ga:hostname, ga:pagepath, ga:date, ga:visitlength, ga:previouspagepath, ga:nextpagepath";
  
  private static Integer MAX_RESULTS = 10000;
  private static String BEGIN_DATE = "2009-01-01";
  private static String END_DATE = "2013-12-31";

  private static String DATASET = "dataset.xml";
  
  /**
   * Main demo. This first initializes an analytics service object. It then uses the Google
   * Analytics Management API to get the first profile ID for the authorized user. It then uses the
   * Core Reporting API to retrieve the top 25 organic search terms. Finally the results are printed
   * to the screen. If an API error occurs, it is printed here.
   *
   * @param args command line args.
   */
  public static void main(String[] args) {
    try {
      HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
      Analytics analytics = initializeAnalytics();
      String profileId = getFirstProfileId(analytics);
      if (profileId == null) {
        System.err.println("No profiles found.");
      } else {
        //GaData data = executeDataQuery(analytics, profileId, BEGIN_DATE, END_DATE, metrics_table1, dimensions_table1);
        GaData data = executeDataQuery(analytics, profileId, BEGIN_DATE, END_DATE, metrics_table2, dimensions_table2);
        //GaData data = executeDataQuery(analytics, profileId, BEGIN_DATE, END_DATE, metrics_table3, dimensions_table3);
        filterMovies(data, 2, 1);
        writeToCSV(data);
        printGaData(data);
        printQueryInfo(data);
        printPaginationInfo(data);
        printResponseInfo(data);
        HttpRequestFactory factory = analytics.getRequestFactory();
        while (data.getNextLink() != null) 
        {
          GenericUrl url = new GenericUrl(data.getNextLink());
          HttpResponse response = factory.buildGetRequest(url).execute();
          data = data.getFactory().fromString(response.parseAsString(), GaData.class);
          filterMovies(data, 2, 1);
          writeToCSV(data);
        }
      }
    } catch (GoogleJsonResponseException e) {
      System.err.println("There was a service error: " + e.getDetails().getCode() + " : "
          + e.getDetails().getMessage());
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  /**
   * @param data
   * @param insertIndex the index where to insert the column about movies
   * @return 
   * @throws ParserConfigurationException 
   * @throws IOException 
   * @throws SAXException 
   * @throws XPathExpressionException 
   */
  private static GaData filterMovies(GaData data, int insertIndex, int pagePathIndex) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
    //Get list of movies
    DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();   
    domFactory.setIgnoringComments(true);
    DocumentBuilder builder = domFactory.newDocumentBuilder();
    Document doc = builder.parse(new File(DATASET));
    XPath xPath = XPathFactory.newInstance().newXPath();
    ArrayList<String> dataset = new ArrayList<String>();    
    NodeList nodeList = (NodeList) xPath.compile("OAI-PMH/ListRecords/record/metadata//*[name()='oai_oi:oi']//*[name()='oi:attributionURL']").evaluate(doc,
            XPathConstants.NODESET);
    for (int i = 0; i < nodeList.getLength(); i++) {
        if (nodeList.item(i).getFirstChild() != null){
            dataset.add(nodeList.item(i).getFirstChild().getNodeValue());
        }
        else {
            dataset.add("null");
        }
    }
    
    //Rewrite urls to match it with pagepaths
    for (int i = 0; i < dataset.size(); i++) {
      String url = dataset.get(i);
      dataset.set(i, rewriteUrl(url));
    }
    
    //Add columnheader IsMovie
    List<ColumnHeaders> columnHeaders = data.getColumnHeaders();
    ColumnHeaders header = new ColumnHeaders().setName("IsMovie");
    columnHeaders.add(insertIndex, header);
    data.setColumnHeaders(columnHeaders);
    
    //Add results to row
    List<List<String>> rows = data.getRows();
    for (int i = 0; i < rows.size(); i++) {
      List<String> row = rows.get(i);
      String movieURL = rewriteMovieUrl(row.get(pagePathIndex));
      
      if (dataset.contains(movieURL)) {
        row.add(insertIndex, "Yes");
      }
      else {
        row.add(insertIndex, "No");
      }      
    }
    
    return data;
  }

  /**
   * @param url
   * @return
   */
  private static String rewriteMovieUrl(String url) {
    String result = "";    
    String[] results = url.split("/");
    
    if (results.length > 1) {
      if (results.length > 2) {
        result = results[1] + "/" + results[2];
      }
      
      else {
        result = results[1];
      }
    }
    else {
      result = "/";
    }
       
    return result;
  }

  /**
   * @param url
   * @return
   */
  private static String rewriteUrl(String url) {
    String result = "";    
    String[] results = url.split("/");
    result = results[3] + "/" + results[4];    
    return result;
  }  

  /** Authorizes the installed application to access user's protected data. */
  private static Credential authorize() throws Exception {
    // load client secrets
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
        JSON_FACTORY, new InputStreamReader(
            HelloAnalyticsApiSample.class.getResourceAsStream("/client_secrets.json")));
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      System.out.println(
          "Enter Client ID and Secret from https://code.google.com/apis/console/?api=analytics "
          + "into analytics-cmdline-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }
    // set up authorization code flow
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets,
        Collections.singleton(AnalyticsScopes.ANALYTICS_READONLY)).setDataStoreFactory(
        DATA_STORE_FACTORY).build();
    // authorize
    return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
  }

  /**
   * Performs all necessary setup steps for running requests against the API.
   *
   * @return An initialized Analytics service object.
   *
   * @throws Exception if an issue occurs with OAuth2Native authorize.
   */
  private static Analytics initializeAnalytics() throws Exception {
    // Authorization.
    Credential credential = authorize();

    // Set up and return Google Analytics API client.
    return new Analytics.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
        APPLICATION_NAME).build();
  }

  /**
   * Returns the first profile id by traversing the Google Analytics Management API. This makes 3
   * queries, first to the accounts collection, then to the web properties collection, and finally
   * to the profiles collection. In each request the first ID of the first entity is retrieved and
   * used in the query for the next collection in the hierarchy.
   *
   * @param analytics the analytics service object used to access the API.
   * @return the profile ID of the user's first account, web property, and profile.
   * @throws IOException if the API encounters an error.
   */
  private static String getFirstProfileId(Analytics analytics) throws IOException {
    String profileId = null;

    // Query accounts collection.
    Accounts accounts = analytics.management().accounts().list().execute();

    if (accounts.getItems().isEmpty()) {
      System.err.println("No accounts found");
    } else {
      String firstAccountId = accounts.getItems().get(0).getId();

      // Query webproperties collection.
      Webproperties webproperties =
          analytics.management().webproperties().list(firstAccountId).execute();

      if (webproperties.getItems().isEmpty()) {
        System.err.println("No Webproperties found");
      } else {
        String firstWebpropertyId = webproperties.getItems().get(0).getId();

        // Query profiles collection.
        Profiles profiles =
            analytics.management().profiles().list(firstAccountId, firstWebpropertyId).execute();

        if (profiles.getItems().isEmpty()) {
          System.err.println("No profiles found");
        } else {
          profileId = profiles.getItems().get(0).getId();
        }
      }
    }
    return profileId;
  }

  /**
   * The Core Reporting API is used to retrieve this data.
   *
   * @param analytics the analytics service object used to access the API.
   * @param profileId the profile ID from which to retrieve data.
   * @param startDate the start date
   * @param endDate the end date
   * @param metrics the metrics for which to retrieve data
   * @param dimensions the dimensions for which to retrieve data
   * @return the response from the API.
   * @throws IOException tf an API error occured.
   */
  private static GaData executeDataQuery(Analytics analytics, String profileId, String startDate, String endDate, String metrics, String dimensions) throws IOException {
    GaData data = analytics.data().ga().get("ga:" + profileId, // Table Id. ga: + profile id.
        startDate, // Start date.
        endDate, // End date.
        metrics) // Metrics.
        .setDimensions(dimensions)
        .setMaxResults(MAX_RESULTS)
        .execute();
    
    return data;
  }
  
  private static void writeToCSV(GaData results) throws IOException{
    boolean created = false;
    File file = new File("test.csv");
    
    if (!file.exists()) {
      file.createNewFile();
      created = true;
    }
    
    FileWriter writer = new FileWriter(file.getName(), true);
    if (results.getRows() == null || results.getRows().isEmpty()) {
      System.out.println("No results Found.");
    }
    else {
      if (created) {
        for (ColumnHeaders header : results.getColumnHeaders()) {
          writer.append(header.toString().replaceAll(",", ";") + ",");
        }
        writer.append('\n');
      }
      
      for (List<String> row : results.getRows()) {
        for (String column : row) {
          writer.append(column + ",");
        }
        writer.append('\n');
      }      
    }

    writer.flush();
    writer.close();
  }
    
  /**
   * Prints the output from the Core Reporting API. The profile name is printed along with each
   * column name and all the data in the rows.
   *
   * @param results data returned from the Core Reporting API.
   * @throws IOException 
   */
  private static void printGaData(GaData results) throws IOException {
    System.out.println(
        "printing results for profile: " + results.getProfileInfo().getProfileName());

    if (results.getRows() == null || results.getRows().isEmpty()) {
      System.out.println("No results Found.");
    } else {

      // Print column headers.
      for (ColumnHeaders header : results.getColumnHeaders()) {
        System.out.printf("%30s", header.getName());
      }
      System.out.println();

      // Print actual data.
      for (List<String> row : results.getRows()) {
        for (String column : row) {
          System.out.printf("%30s", column);
        }
        System.out.println();
      }

      System.out.println();
    }
  }
  
  private static void printResponseInfo(GaData gaData) {
    System.out.println("Contains Sampled Data: " + gaData.getContainsSampledData());
    System.out.println("Kind: " + gaData.getKind());
    System.out.println("ID:" + gaData.getId());
    System.out.println("Self link: " + gaData.getSelfLink());
  }
  
  private static void printQueryInfo(GaData gaData) {
    Query query = gaData.getQuery();

    System.out.println("Ids: " + query.getIds());
    System.out.println("Start Date: " + query.getStartDate());
    System.out.println("End Date: " + query.getEndDate());
    System.out.println("Metrics: " + query.getMetrics()); // List
    System.out.println("Dimensions: " + query.getDimensions());
    System.out.println("Sort: " + query.getSort()); // List
    System.out.println("Segment: " + query.getSegment());
    System.out.println("Filters: " + query.getFilters());
    System.out.println("Start Index: " + query.getStartIndex());
    System.out.println("Max Results: " + query.getMaxResults());
  }
  
  private static void printPaginationInfo(GaData gaData) {
    System.out.println("Items Per Page: " + gaData.getItemsPerPage());
    System.out.println("Total Results: " + gaData.getTotalResults());
    System.out.println("Previous Link: " + gaData.getPreviousLink());
    System.out.println("Next Link: " + gaData.getNextLink());
  }
}
