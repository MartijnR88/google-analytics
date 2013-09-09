/*
 * Copyright (c) 2011 Google Inc.
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
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.analytics.Analytics;
import com.google.api.services.analytics.AnalyticsScopes;
import com.google.api.services.analytics.model.Accounts;
import com.google.api.services.analytics.model.GaData;
import com.google.api.services.analytics.model.Profiles;
import com.google.api.services.analytics.model.Webproperties;
import com.google.api.services.analytics.model.GaData.ColumnHeaders;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

/**
 * @author af13020@google.com (Your Name Here)
 *
 */
public class HelloAnalyticsTutorial {
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private static final java.io.File DATA_STORE_DIR =
        new java.io.File(System.getProperty("user.home"), ".store/analytics_sample");
    private static FileDataStoreFactory dataStoreFactory;
    
    public static void main(String[] args) throws IOException {
      dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
      try {
        Analytics analytics = initializeAnalytics();
        String profileId = getFirstProfileId(analytics);
        
        if (profileId != null) {
          GaData gaData = getResults(analytics, profileId);
          
          printResults(gaData);
        }
      } catch (GoogleJsonResponseException e) {
        System.err.println("There was a service error: " + e.getDetails().getCode() + " : "
            + e.getDetails().getMessage());
      }
      catch (Throwable t) {
        t.printStackTrace();
      }
    }
    
    /**
     * @param results
     */
    private static void printResults(GaData results) {
      if (results != null && !results.getRows().isEmpty()){
        System.out.println("View (Profile) Name: " + results.getProfileInfo().getProfileName());
        System.out.println("Account ID: " + results.getProfileInfo().getAccountId());
        System.out.println("Internal WebProperty ID: " + results.getProfileInfo().getInternalWebPropertyId());
        System.out.println("WebProperty ID: " + results.getProfileInfo().getWebPropertyId());
        System.out.println("Profile ID: " + results.getProfileInfo().getProfileId());
        
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
      
      else {
        System.out.println("No results found");
      }
    }

    /**
     * @param analytics
     * @param profileId
     * @return
     * @throws IOException 
     */
    private static GaData getResults(Analytics analytics, String profileId) throws IOException {
      return analytics.data().ga().get("ga:" + profileId, "2012-03-03", "2012-03-03", "ga:visits").execute();
    }

    /**
     * @param analytics
     * @return
     * @throws IOException 
     */
    private static String getFirstProfileId(Analytics analytics) throws IOException {
      String profileId = null;
      
      Accounts accounts = analytics.management().accounts().list().execute();
      
      if (accounts.getItems().isEmpty()) {
        System.err.println("No accounts found");
      }
      else {
        String firstAccountId = accounts.getItems().get(0).getId();
        
        Webproperties webproperties = analytics.management().webproperties().list(firstAccountId).execute();
        
        if (webproperties.getItems().isEmpty()) {
          System.err.println("No Webproperties found");
        }
        else {
          String firstWebPropertyId = webproperties.getItems().get(0).getId();
          
          Profiles profiles = analytics.management().profiles().list(firstAccountId, firstWebPropertyId).execute();
          
          if (profiles.getItems().isEmpty()) {
            System.err.println("No views (profiles) found");
          }
          else {
            profileId = profiles.getItems().get(0).getId();
          }
        }
      }
      return profileId;
    }

    private static Analytics initializeAnalytics() throws Exception {          
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
              dataStoreFactory).build();
          // authorize
          Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");

          Analytics analytics = new Analytics.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
              .setApplicationName("Hello-Analytics-API-Sample")
              .setHttpRequestInitializer(credential)
              .build();
          
          return analytics;
    }
}
