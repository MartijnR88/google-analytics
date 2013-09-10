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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;


/**
 * This is a basic hello world sample for the Google Analytics API. It is designed to run from the
 * command line and will prompt a user to grant access to their data. Once complete, the sample will
 * traverse the Management API hierarchy by going through the authorized user's first account, first
 * web property, and finally the first profile and retrieve the first profile id. This ID is then
 * used with the Core Reporting API to retrieve the top 25 organic search terms.
 *
 * @author api.nickm@gmail.com
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
  private static final String metrics = "ga:adClicks,ga:adCost,ga:appviews,ga:appviewsPerVisit,ga:avgDomainLookupTime,ga:avgDomContentLoadedTime,ga:avgDomInteractiveTime,ga:avgEventValue,ga:avgPageDownloadTime,ga:avgPageLoadTime,ga:avgRedirectionTime,ga:avgScreenviewDuration,ga:avgSearchDepth,ga:avgSearchDuration,ga:avgSearchResultViews,ga:avgServerConnectionTime,ga:avgServerResponseTime,ga:avgTimeOnPage,ga:avgTimeOnSite,ga:avgUserTimingValue,ga:bounces,ga:costPerConversion,ga:costPerGoalConversion,ga:costPerTransaction,ga:CPC,ga:CPM,ga:CTR,ga:domainLookupTime,ga:domContentLoadedTime,ga:domInteractiveTime,ga:domLatencyMetricsSample,ga:entranceBounceRate,ga:entranceRate,ga:entrances,ga:eventsPerVisitWithEvent,ga:eventValue,ga:exceptions,ga:exceptionsPerScreenview,ga:exitRate,ga:exits,ga:fatalExceptions,ga:fatalExceptionsPerScreenview,ga:goalAbandonRateAll,ga:goalAbandonsAll,ga:goalCompletionsAll,ga:goalConversionRateAll,ga:goalStartsAll,ga:goalValueAll,ga:goalValueAllPerSearch,ga:goalValuePerVisit,ga:goalXXAbandonRate,ga:goalXXAbandons,ga:goalXXCompletions,ga:goalXXConversionRate,ga:goalXXStarts,ga:goalXXValue,ga:impressions,ga:itemQuantity,ga:itemRevenue,ga:itemsPerPurchase,ga:localItemRevenue,ga:localTransactionRevenue,ga:localTransactionShipping,ga:localTransactionTax,ga:margin,ga:metricXX,ga:newVisits,ga:organicSearches,ga:pageDownloadTime,ga:pageLoadSample,ga:pageLoadTime,ga:pageValue,ga:pageviews,ga:pageviewsPerVisit,ga:percentNewVisits,ga:percentSearchRefinements,ga:percentVisitsWithSearch,ga:redirectionTime,ga:revenuePerItem,ga:revenuePerTransaction,ga:ROI,ga:RPC,ga:screenviews,ga:screenviewsPerSession,ga:searchDepth,ga:searchDuration,ga:searchExitRate,ga:searchExits,ga:searchGoalConversionRateAll,ga:searchGoalXXConversionRate,ga:searchRefinements,ga:searchResultViews,ga:searchUniques,ga:searchVisits,ga:serverConnectionTime,ga:serverResponseTime,ga:socialActivities,ga:socialInteractions,ga:socialInteractionsPerVisit,ga:speedMetricsSample,ga:timeOnPage,ga:timeOnScreen,ga:timeOnSite,ga:totalEvents,ga:totalValue,ga:transactionRevenue,ga:transactionRevenuePerVisit,ga:transactions,ga:transactionShipping,ga:transactionsPerVisit,ga:transactionTax,ga:uniqueAppviews,ga:uniqueEvents,ga:uniquePageviews,ga:uniquePurchases,ga:uniqueScreenviews,ga:uniqueSocialInteractions,ga:userTimingSample,ga:userTimingValue,ga:visitBounceRate,ga:visitors,ga:visits,ga:visitsWithEvent";
  /**Maximum of 7 dimensions per request */
  private static final String dimensions = "ga:adContent,ga:adDestinationUrl,ga:adDisplayUrl,ga:adDistributionNetwork,ga:adFormat,ga:adGroup,ga:adMatchedQuery,ga:adMatchType,ga:adPlacementDomain,ga:adPlacementUrl,ga:adSlot,ga:adSlotPosition,ga:adTargetingOption,ga:adTargetingType,ga:adwordsAdGroupID,ga:adwordsCampaignID,ga:adwordsCreativeID,ga:adwordsCriteriaID,ga:adwordsCustomerID,ga:affiliation,ga:appId,ga:appInstallerId,ga:appName,ga:appVersion,ga:browser,ga:browserVersion,ga:campaign,ga:city,ga:continent,ga:country,ga:currencyCode,ga:customVarNameXX,ga:customVarValueXX,ga:date,ga:dateHour,ga:day,ga:dayOfWeek,ga:dayOfWeekName,ga:daysSinceLastVisit,ga:daysToTransaction,ga:deviceCategory,ga:dimensionXX,ga:eventAction,ga:eventCategory,ga:eventLabel,ga:exceptionDescription,ga:exitPagePath,ga:exitScreenName,ga:experimentId,ga:experimentVariant,ga:flashVersion,ga:fullReferrer,ga:goalCompletionLocation,ga:goalPreviousStep1,ga:goalPreviousStep2,ga:goalPreviousStep3,ga:hasSocialSourceReferral,ga:hostname,ga:hour,ga:isMobile,ga:isoWeek,ga:isTablet,ga:javaEnabled,ga:keyword,ga:landingPagePath,ga:landingScreenName,ga:language,ga:latitude,ga:longitude,ga:medium,ga:metro,ga:mobileDeviceBranding,ga:mobileDeviceInfo,ga:mobileDeviceMarketingName,ga:mobileDeviceModel,ga:mobileInputSelector,ga:month,ga:networkDomain,ga:networkLocation,ga:nextPagePath,ga:nthDay,ga:nthMonth,ga:nthWeek,ga:operatingSystem,ga:operatingSystemVersion,ga:pageDepth,ga:pagePath,ga:pagePathLevel1,ga:pagePathLevel2,ga:pagePathLevel3,ga:pagePathLevel4,ga:pageTitle,ga:previousPagePath,ga:productCategory,ga:productName,ga:productSku,ga:referralPath,ga:region,ga:screenColors,ga:screenDepth,ga:screenName,ga:screenResolution,ga:searchCategory,ga:searchDestinationPage,ga:searchKeyword,ga:searchKeywordRefinement,ga:searchStartPage,ga:searchUsed,ga:secondPagePath,ga:socialActivityAction,ga:socialActivityContentUrl,ga:socialActivityDisplayName,ga:socialActivityEndorsingUrl,ga:socialActivityNetworkAction,ga:socialActivityPost,ga:socialActivityTagsSummary,ga:socialActivityTimestamp,ga:socialActivityUserHandle,ga:socialActivityUserPhotoUrl,ga:socialActivityUserProfileUrl,ga:socialEngagementType,ga:socialInteractionAction,ga:socialInteractionNetwork,ga:socialInteractionNetworkAction,ga:socialInteractionTarget,ga:socialNetwork,ga:source,ga:sourceMedium,ga:subContinent,ga:transactionId,ga:userDefinedValue,ga:userTimingCategory,ga:userTimingLabel,ga:userTimingVariable,ga:visitCount,ga:visitLength,ga:visitorType,ga:visitsToTransaction,ga:week,ga:year,ga:yearMonth,ga:yearWeek";
;

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
        GaData data = executeDataQuery(analytics, profileId, "2009-01-01", "2013-12-31", "ga:visitors, ga:visits", "ga:landingpagepath, ga:date, ga:visitlength", "");
        writeToCSV(data);
        printGaData(data);
        printQueryInfo(data);
        printPaginationInfo(data);
        printResponseInfo(data);
      }
    } catch (GoogleJsonResponseException e) {
      System.err.println("There was a service error: " + e.getDetails().getCode() + " : "
          + e.getDetails().getMessage());
    } catch (Throwable t) {
      t.printStackTrace();
    }
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

//  /**
//   * Returns the top 25 organic search keywords and traffic source by visits. The Core Reporting API
//   * is used to retrieve this data.
//   *
//   * @param analytics the analytics service object used to access the API.
//   * @param profileId the profile ID from which to retrieve data.
//   * @return the response from the API.
//   * @throws IOException tf an API error occured.
//   */
//  private static GaData executeDataQuery(Analytics analytics, String profileId) throws IOException {
//    return analytics.data().ga().get("ga:" + profileId, // Table Id. ga: + profile id.
//  "2009-01-01", // Start date.
//  "2013-12-31", // End date.
//  "ga:totalevents") // Metrics.
//  .setMaxResults(25)
//  .execute();
//  }
  
  private static GaData executeDataQuery(Analytics analytics, String profileId, String startDate, String endDate, String metrics, String dimensions, String sort) throws IOException {
    GaData data = analytics.data().ga().get("ga:" + profileId, // Table Id. ga: + profile id.
        startDate, // Start date.
        endDate, // End date.
        metrics) // Metrics.
        .setDimensions(dimensions)
        //.setSort(sort)
        .setMaxResults(100)
        .execute();
    
    if (data.getNextLink() != null) {
      GenericUrl url = new GenericUrl(data.getNextLink());
      HttpResponse response = analytics.getRequestFactory().buildGetRequest(url).execute();
      data = data.getFactory().fromString(response.parseAsString(), GaData.class); 
      System.out.println("Next 10");
        System.out.println(response.parseAsString());
    }
    
    return data;
  }
  
  private static void writeToCSV(GaData results) throws IOException{
    FileWriter writer = new FileWriter("test.csv");
    if (results.getRows() == null || results.getRows().isEmpty()) {
      System.out.println("No results Found.");
    }
    else {
      for (ColumnHeaders header : results.getColumnHeaders()) {
        writer.append(header.getName() + ",");
      }
      writer.append('\n');
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
   */
  private static void printGaData(GaData results) {
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
