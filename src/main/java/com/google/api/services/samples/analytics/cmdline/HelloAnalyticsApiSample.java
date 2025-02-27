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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
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
 * This is a file to retrieve Google Analytics data. Once complete, it will
 * traverse the Management API hierarchy by going through the authorized user's first account, first
 * web property, and finally the first profile and retrieve the first profile id. This ID is then
 * used with the Core Reporting API to retrieve the top X organic search terms.
 *
 * @author 
 */
public class HelloAnalyticsApiSample {

  /**
   * Be sure to specify the name of the application. If the application name is {@code null} or
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
  private static final String METRICS_TABLE1 = "ga:visits,ga:visitors,ga:timeOnPage,ga:avgTimeOnPage,ga:pageviews";
  /**Maximum of 7 dimensions per request */
  private static final String DIMENSIONS_TABLE1 = "ga:pagepath,ga:date,ga:visitlength,ga:city";
  /** Index of column where to insert the IsMovie? column, the first column has index 0 */
  private static final int ISMOVIE_INDEX_TABLE1 = 1;
  
  /**Maximum of 10 metrics per request */
  private static final String METRICS_TABLE2 = "ga:visits, ga:visitors, ga:pageviews, ga:uniquepageviews,ga:bounces";
  /**Maximum of 7 dimensions per request */
  private static final String DIMENSIONS_TABLE2 = "ga:date, ga:landingpagepath, ga:secondpagepath, ga:exitpagepath,ga:city,ga:visitLength";
  /** Indices of columns where to insert the IsMovie? columns, the first column has index 0 */
  private static final int ISMOVIE_INDEX1_TABLE2 = 2;
  private static final int ISMOVIE_INDEX2_TABLE2 = 4;
  private static final int ISMOVIE_INDEX3_TABLE2 = 6;
  
  /**Maximum of 10 metrics per request */
  private static final String METRICS_TABLE3 = "ga:visits, ga:visitors, ga:pageviews";
  /**Maximum of 7 dimensions per request */
  private static final String DIMENSIONS_TABLE3 = "ga:pagepath, ga:date, ga:visitLength, ga:previousPagePath, ga:city";
  /** Indices of columns where to insert the IsMovie? columns, the first column has index 0 */
  private static final int ISMOVIE_INDEX1_TABLE3 = 1;
  private static final int ISMOVIE_INDEX2_TABLE3 = 5;
  /** Indices to indicate which column contains the pagepath and which contains the previous pagepath, used to calculate if they are related */
  private static final int PAGEPATHINDEX = 0;
  private static final int PREVIOUSPAGEPATHINDEX = 4;
  /** Indices of columns where to insert the HasRelationship column, the first column has index 0 */
  private static final int HASRELATIONSHIPINDEX = 6;
  
  /** The maximum number of results per page */
  private static final int MAX_RESULTS = 10000;
  private static final String BEGIN_DATE = "2009-01-01";
  private static final String END_DATE = "2013-12-31";

  /** Dataset of OpenBeelden, used to determine if a pagepath is a movie */
  private static final String DATASET = "dataset.xml";
  /** Tags belonging to the dataset, got it via: http://www.openbeelden.nl/feeds/tags-html.jspx */
  private static final String TAGS = "tags.xml";
  
  /**
   * Main. This first initializes an analytics service object. It then uses the Google
   * Analytics Management API to get the first profile ID for the authorized user. It then uses the
   * Core Reporting API to retrieve the top X organic search terms. Finally the results are written to a file. If an API error occurs, it is printed here.
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
        //Number of table to generate
        int table = 3;
        String writeToFile = "table" + table + ".csv";
        GaData data = null;
        
        //Execute query for the specified table
        if (table == 1) {
          data = executeDataQuery(analytics, profileId, BEGIN_DATE, END_DATE, METRICS_TABLE1, DIMENSIONS_TABLE1);
        }
        else if (table == 2) {
          data = executeDataQuery(analytics, profileId, BEGIN_DATE, END_DATE, METRICS_TABLE2, DIMENSIONS_TABLE2);
        }
        else {
          data = executeDataQuery(analytics, profileId, BEGIN_DATE, END_DATE, METRICS_TABLE3, DIMENSIONS_TABLE3);
        } 
        
        //Print metadata for the query
        printGaData(data);
        printQueryInfo(data);
        printPaginationInfo(data);
        printResponseInfo(data);
        
        //Write gathered data to CSV
        writeToCSV(data, writeToFile);
        
        //Get all results, since the maximum number of results per page are 10000
        HttpRequestFactory factory = analytics.getRequestFactory();
        while (data.getNextLink() != null) 
        {
          GenericUrl url = new GenericUrl(data.getNextLink());
          System.out.println(data.getNextLink());
          HttpResponse response = factory.buildGetRequest(url).execute();
          data = data.getFactory().fromString(response.parseAsString(), GaData.class);
          writeToCSV(data, writeToFile);
        }
        
        //Execute isMovieFilter
        if (table == 1) {
          writeToCSV(filterIsMovie(loadCSV("table1.csv"),Arrays.asList(ISMOVIE_INDEX_TABLE1)),"table1withIsMovie.csv");  
        }
        else if (table == 2) {
          writeToCSV(filterIsMovie(loadCSV("table2.csv"),Arrays.asList(ISMOVIE_INDEX1_TABLE2, ISMOVIE_INDEX2_TABLE2, ISMOVIE_INDEX3_TABLE2)),"table2withIsMovie.csv");  
        }
        else {
          writeToCSV(filterIsMovie(loadCSV("table3.csv"),Arrays.asList(ISMOVIE_INDEX1_TABLE3, ISMOVIE_INDEX2_TABLE3)),"table3withIsMovie.csv");  
        }
       
        //Execute movieIdFilter, this changes the pagepaths to the movieIndexNumbers from the dataset of OpenBeelden. The numbers are the indices of the columns containing the paths in the table.
        if (table == 1){
          writeToCSV(filterMovieId(loadCSV("table1withIsMovie.csv"), Arrays.asList(0)), "table1withMovieId.csv");  
        }
        else if (table == 2) {
          writeToCSV(filterMovieId(loadCSV("table2withIsMovie.csv"), Arrays.asList(1,3,5)), "table2withMovieId.csv");  
        }
        else {
          writeToCSV(filterMovieId(loadCSV("table3withIsMovie.csv"), Arrays.asList(PAGEPATHINDEX,PREVIOUSPAGEPATHINDEX)), "table3withMovieId.csv");  
        }  
        
        //Execute hasRelationship filter. 
        writeToCSV(filterRelationship(loadCSV("table3withMovieId.csv"), HASRELATIONSHIPINDEX, PAGEPATHINDEX, PREVIOUSPAGEPATHINDEX),"table3withRelationship.csv");
      }
    } catch (GoogleJsonResponseException e) {
      System.err.println("There was a service error: " + e.getDetails().getCode() + " : "
          + e.getDetails().getMessage());
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
  
  @SuppressWarnings("resource")
  /** Loads a CSV in a matrix of Strings*/
  private static List<List<String>> loadCSV(String filename) throws IOException {
    List<List<String>> result = new ArrayList<List<String>>();
    String line = "";
    BufferedReader br = null;
    
    br = new BufferedReader(new FileReader(filename));
    while ((line = br.readLine()) != null) {
      List<String> row = new ArrayList<String>();
      String[] temprow = line.split(",");
      for (String s : temprow){
        row.add(s);
      }
      result.add(row);
    }
    
    System.out.println("CSV loaded");
    
    return result;
  }
  
  /** Write results to CSV, when the results are in the form List<List<String>> which is the case after reading from CSV */
  private static void writeToCSV(List<List<String>> results, String filename) throws IOException {
    boolean created = false;
    File file = new File(filename);
    
    //Create new file, if it doesn't exist
    if (!file.exists()) {
      file.createNewFile();
      created = true;
    }
    
    FileWriter writer = new FileWriter(file.getName(), true);
    if (results.size() == 0) {
      System.out.println("No results Found.");
    }
    
    else {
      if (created) {
      //First print all headers, where "," are replaced by ";" because it is written to CSV
        for (String header : results.get(0)) {
          writer.append(header.replaceAll(",", ";") + ",");
        }
        writer.append('\n');
      }
      
      //Print row per row
      List<List<String>> rows = results;
      for (int i = 1; i < rows.size(); i++) {
        List<String> row = rows.get(i);
        for (String column : row) {
          writer.append(column.replaceAll(",", ";") + ",");
        }
        writer.append('\n');
        System.out.println("Appended row: " + i + "of " + rows.size() + " rows");
      }      
    }

    writer.flush();
    writer.close();
  }
  
  /** Same writeToCSV function as above, except that the input is GaData, which is the object that results from a Google Analytics query */
  private static void writeToCSV(GaData results, String filename) throws IOException{
    boolean created = false;
    File file = new File(filename);
    
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
          writer.append(column.replaceAll(",", ";") + ",");
        }
        writer.append('\n');
      }      
    }

    writer.flush();
    writer.close();
  }
  
  /** Is Movie Filter */
  private static List<List<String>> filterIsMovie(List<List<String>> data, List<Integer> list) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
    //Get list of movies
    NodeList nodeList = getNodes(DATASET, "OAI-PMH/ListRecords/record/metadata//*[name()='oai_oi:oi']//*[name()='oi:attributionURL']");   
    ArrayList<String> dataset = new ArrayList<String>();    

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
      dataset.set(i, rewriteOpenImagesUrl(url));
    }
    
    for (int j = 0; j < list.size(); j++) {
      int insert = list.get(j);
      //Add columnheader IsMovie
      List<String> columnHeaders = data.get(0);
      columnHeaders.add(insert, "isMovie");
      data.set(0, columnHeaders);
      
      //Add results to row
      for (int i = 1; i < data.size(); i++) {
        List<String> row = data.get(i);
        String movieURL = rewriteGAUrl(row.get(insert-1));
        
        if (dataset.contains(movieURL)) {
          row.add(insert, "Yes");
        }
        else {
          row.add(insert, "No");
        }      
      }
    }
    return data;
  }

  /** Movie Id Filter */
  private static List<List<String>> filterMovieId(List<List<String>> data, List<Integer> insertIndex) {
    //Insert Movie Id everywhere necessary, so everywhere for which an insertIndex is given
    for (int j = 0; j < insertIndex.size(); j++) {
      int insert = insertIndex.get(j);
      
      //Get data
      for (int i = 1; i < data.size(); i++) {
        List<String> row = data.get(i);
        //If isMovie is yes, the URL is a movie, so strip the index from the URL
        if (row.get(insert+1).equals("Yes")) {
          String video = row.get(insert);
          String[] parts = video.split("/");
          parts = parts[2].split("\\.");
          System.out.println(video);
          row.set(insert,parts[0]);
        }
      }
    }
    
    return data;
  }
  
  /** HasRelationship (by tags) Filter */
  private static ArrayList<String> hasRelationship(String attributionURLvideo1, String attributionURLvideo2) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
    ArrayList<String> results = new ArrayList<String>();
    //Get tags
    NodeList nodes = getNodes(TAGS, "tags/tag");

    for (int i = 0; i < nodes.getLength(); i++) {
      //Get a tag
      Node tag = nodes.item(i);
      
      if (tag.getNodeType() == Node.ELEMENT_NODE) {
        Element firstTag = (Element)tag;
        NodeList videos = firstTag.getElementsByTagName("video");
        ArrayList<String> videoNumbers = new ArrayList<String>();
        
        //For every video for a certain tag, add the video number
        for (int j = 0; j < videos.getLength(); j++){
          Node video = videos.item(j);
          
          if (video.getNodeType() == Node.ELEMENT_NODE) {
            Element firstVideo = (Element)video;
            String videoNumber = firstVideo.getAttribute("number");
            videoNumbers.add(videoNumber);
          }
        }
        
        //If all video numbers are added for a tag, check if two videos share that tag
        if (videoNumbers.contains(attributionURLvideo1) && videoNumbers.contains(attributionURLvideo2) && !attributionURLvideo1.equals(attributionURLvideo2)) {
          results.add(firstTag.getElementsByTagName("name").item(0).getChildNodes().item(0).getNodeValue());
        }
      }
    }

    return results;
  }

  /** Relationship filter */
  private static List<List<String>> filterRelationship(List<List<String>> data, int insertIndex, int videoIndex1, int videoIndex2) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException {
    //Add headers
    List<String> columnHeaders = data.get(0);
    System.out.println("Columnheaders: " + columnHeaders.get(0));
    String header = "hasRelationship";
    String header2 = "relationship";
    columnHeaders.add(insertIndex, header);
    columnHeaders.add(insertIndex+1, header2);
    data.set(0, columnHeaders);
    
    System.out.println("Updated columnheaders");
    
    List<List<String>> rows = data;
    
    //Begins with 1, because the 0th row are the columnheaders
    for (int i = 1; i < rows.size(); i++) {
      List<String> row = rows.get(i);
      System.out.println("Updating row: " + i + " of " + rows.size() + " rows");
      
      //Only if both "items" are videos we need to check
      if (row.get(videoIndex1+1).equals("Yes") && row.get(videoIndex2+1).equals("Yes")) {
      ArrayList<String> relationship = new ArrayList<String>();
      relationship = hasRelationship(row.get(videoIndex1), row.get(videoIndex2));
      String relationships = "";
      
      //If relationship contains multiple tags, the videos are related
      if (relationship.size() > 0) {
        row.add(insertIndex, "True");
        //Print all tags, via which they are related
        for (int j = 0; j < relationship.size(); j++){
          relationships = relationships + relationship.get(j) + "; ";
        }
        row.add(insertIndex+1, relationships);
      }
      //They don't share a tag, so they are not related
      else {
        row.add(insertIndex, "False");
        row.add(insertIndex+1, "null");
      }      
      }
      
      //They are not related because one or both URLs is not a video
      else {
        row.add(insertIndex, "False");
        row.add(insertIndex+1, "null");
      }
      System.out.println("Row: " + row.toString() + "\n");
    }
    
    System.out.println("Rows updated");
    
    return data;
  }


  /** Helper class to get Nodes from the dataset (XML-format), used for isMovie Filter and HasRelationship Filter */
  private static NodeList getNodes(String dataset, String expression) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
    DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();   
    domFactory.setIgnoringComments(true);
    DocumentBuilder builder = domFactory.newDocumentBuilder();
    //Parse dataset
    Document doc = builder.parse(new File(dataset));
    XPath xPath = XPathFactory.newInstance().newXPath();
    //Get nodeList from the specified expression
    NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(doc,XPathConstants.NODESET);
    return nodeList;
  }

  /** Rewrite the URLs gathered from Google Analytics to the same format as the URLs in the dataset from OpenBeelden */
  private static String rewriteGAUrl(String url) {
    String result = "";    
    String[] results = url.split("/");
    
    if (results.length > 1) {
      if (results.length > 2) {
        String[] tempresult = results[2].split("\\.");
          result = results[1] + "/" + tempresult[0];
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

  /** Rewrite the URLs from OpenBeelden, so instead of http://openbeelden.nl/media/637704 it will become media/637704 for example */
  private static String rewriteOpenImagesUrl(String url) {
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
  
  /**
   * Prints the Response info.
   *
   * @param results data returned from the Core Reporting API.
   */
  private static void printResponseInfo(GaData gaData) {
    System.out.println("Contains Sampled Data: " + gaData.getContainsSampledData());
    System.out.println("Kind: " + gaData.getKind());
    System.out.println("ID:" + gaData.getId());
    System.out.println("Self link: " + gaData.getSelfLink());
  }
  
  /**
   * Prints the Query info.
   *
   * @param results data returned from the Core Reporting API.
   */
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
  
  /**
   * Prints the Pagination info.
   *
   * @param results data returned from the Core Reporting API.
   */
  private static void printPaginationInfo(GaData gaData) {
    System.out.println("Items Per Page: " + gaData.getItemsPerPage());
    System.out.println("Total Results: " + gaData.getTotalResults());
    System.out.println("Previous Link: " + gaData.getPreviousLink());
    System.out.println("Next Link: " + gaData.getNextLink());
  }
}
