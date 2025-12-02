package com.mcpkyb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpkyb.config.TwitterApiClient;
import com.mcpkyb.model.UserSentimentDTO;
import com.mcpkyb.utils.JsonLoader;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SentimentAnalysisAgent {

    @Autowired
    private TwitterApiClient twitterApiClient;

    @Autowired
    private StanfordCoreNLP stanfordCoreNLP;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> analyzeSentimentByCustomerId(String customerId) throws IOException {
        // Load CRM data and find the customer
        JsonNode crmData = JsonLoader.loadJson("crm.json");
        JsonNode customer = null;

        for (JsonNode c : crmData.get("customers")) {
            if (c.get("customer_id").asText().equals(customerId)) {
                customer = c;
                break;
            }
        }

        if (customer == null) {
            throw new IOException("Customer not found: " + customerId);
        }

        // Get the twitter_id to use as search topic
        String twitterId = customer.get("twitter_id").asText();
        if (twitterId == null || twitterId.trim().isEmpty()) {
            throw new IOException("Twitter ID not found for customer: " + customerId);
        }

        // Perform sentiment analysis
        try {
            System.out.println("Starting sentiment analysis for Twitter ID: " + twitterId);

            // Check if Twitter API client has valid token
            if (twitterApiClient == null) {
                throw new IOException("Twitter API client not initialized");
            }

            // Additional validation - check if token looks valid
            String tokenCheck = System.getenv("TWITTER_API_BEARER_TOKEN");
            if (tokenCheck == null || tokenCheck.trim().isEmpty()) {
                System.err.println("WARNING: TWITTER_API_BEARER_TOKEN environment variable not set!");
            } else {
                System.out.println("Twitter API token configured (length: " + tokenCheck.length() + ")");
            }

            return getSentimentSummaryWithAggregate(twitterId);
        } catch (Exception e) {
            System.err.println("Error performing sentiment analysis for " + twitterId + ": " + e.getMessage());
            throw new IOException("Error performing sentiment analysis: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> getSentimentSummaryWithAggregate(String topic) throws Exception {
        // Simplified Twitter API call - minimal parameters to avoid timeouts
        String url = "https://api.twitter.com/2/tweets/search/recent?query=" + topic + "&max_results=10";
        System.out.println("Making simplified Twitter API call to: " + url);
        CompletableFuture<String> responseFuture = twitterApiClient.executeAsyncWithRateLimit(url);

        // Add timeout to prevent hanging
        String jsonResponse;
        try {
            System.out.println("Waiting for Twitter API response (10s timeout)...");
            jsonResponse = responseFuture.get(10, TimeUnit.SECONDS);
            System.out.println("Received Twitter API response, length: " + jsonResponse.length());
        } catch (Exception e) {
            System.err.println("Twitter API call failed. Exception type: " + e.getClass().getName() + ", Message: '" + e.getMessage() + "', Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "null"));

            // Return sample data instead of empty fallback for demonstration
            System.out.println("Returning sample sentiment data since Twitter API is unavailable");
            return createSampleResponse(topic);
        }

        JsonNode root = objectMapper.readTree(jsonResponse);

        List<Map<String, Object>> tweetsWithSentiment = new ArrayList<>();
        Map<String, int[]> sentimentCounts = new HashMap<>();
        sentimentCounts.put("Positive", new int[]{0});
        sentimentCounts.put("Negative", new int[]{0});
        sentimentCounts.put("Neutral", new int[]{0});

        int tweetCount = 0;
        for (JsonNode tweet : root.path("data")) {
            if (tweetCount >= 5) break; // Still limit to 5 tweets for processing

            String tweetText = tweet.get("text").asText();
            String sentiment = analyzeSentiment(tweetText);

            // Count sentiments
            sentimentCounts.get(sentiment)[0]++;

            // Create simplified tweet object
            Map<String, Object> tweetData = new HashMap<>();
            tweetData.put("id", tweet.get("id").asText());
            tweetData.put("text", tweetText);
            tweetData.put("author", tweet.path("author_id").asText()); // Just show author ID since we don't have username
            tweetData.put("accountType", "Unknown"); // Can't classify without user data
            tweetData.put("sentiment", sentiment);
            tweetData.put("created_at", tweet.path("created_at").asText());

            tweetsWithSentiment.add(tweetData);
            tweetCount++;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("topic", topic);
        response.put("tweets", tweetsWithSentiment);
        response.put("sentiment_summary", Map.of(
            "positive", sentimentCounts.get("Positive")[0],
            "negative", sentimentCounts.get("Negative")[0],
            "neutral", sentimentCounts.get("Neutral")[0],
            "total", tweetCount
        ));
        response.put("analyzed_tweet_count", tweetCount);

        return response;
    }

    private Map<String, Object> createSampleResponse(String topic) {
        List<Map<String, Object>> sampleTweets = new ArrayList<>();

        // Sample tweet 1
        Map<String, Object> tweet1 = new HashMap<>();
        tweet1.put("id", "sample_001");
        tweet1.put("text", "Great customer service from " + topic + " today! Very helpful and responsive. 👍");
        tweet1.put("author", "customer123");
        tweet1.put("accountType", "Personal");
        tweet1.put("sentiment", analyzeSentiment(tweet1.get("text").toString()));
        tweet1.put("created_at", "2024-01-01T10:30:00Z");
        sampleTweets.add(tweet1);

        // Sample tweet 2
        Map<String, Object> tweet2 = new HashMap<>();
        tweet2.put("id", "sample_002");
        tweet2.put("text", topic + " banking services are excellent. Fast processing and secure.");
        tweet2.put("author", "business_user");
        tweet2.put("accountType", "Business");
        tweet2.put("sentiment", analyzeSentiment(tweet2.get("text").toString()));
        tweet2.put("created_at", "2024-01-01T09:15:00Z");
        sampleTweets.add(tweet2);

        // Sample tweet 3
        Map<String, Object> tweet3 = new HashMap<>();
        tweet3.put("id", "sample_003");
        tweet3.put("text", "Waited too long for " + topic + " to respond to my query. Not satisfied.");
        tweet3.put("author", "frustrated_client");
        tweet3.put("accountType", "Personal");
        tweet3.put("sentiment", analyzeSentiment(tweet3.get("text").toString()));
        tweet3.put("created_at", "2024-01-01T08:45:00Z");
        sampleTweets.add(tweet3);

        // Sample tweet 4
        Map<String, Object> tweet4 = new HashMap<>();
        tweet4.put("id", "sample_004");
        tweet4.put("text", "Professional service from " + topic + " team. Highly recommended!");
        tweet4.put("author", "satisfied_customer");
        tweet4.put("accountType", "Personal");
        tweet4.put("sentiment", analyzeSentiment(tweet4.get("text").toString()));
        tweet4.put("created_at", "2024-01-01T07:20:00Z");
        sampleTweets.add(tweet4);

        // Sample tweet 5
        Map<String, Object> tweet5 = new HashMap<>();
        tweet5.put("id", "sample_005");
        tweet5.put("text", topic + " has good online banking features but could improve mobile app.");
        tweet5.put("author", "tech_user");
        tweet5.put("accountType", "Personal");
        tweet5.put("sentiment", analyzeSentiment(tweet5.get("text").toString()));
        tweet5.put("created_at", "2024-01-01T06:10:00Z");
        sampleTweets.add(tweet5);

        // Calculate sentiment summary from sample data
        Map<String, int[]> sentimentCounts = new HashMap<>();
        sentimentCounts.put("Positive", new int[]{0});
        sentimentCounts.put("Negative", new int[]{0});
        sentimentCounts.put("Neutral", new int[]{0});

        for (Map<String, Object> tweet : sampleTweets) {
            String sentiment = (String) tweet.get("sentiment");
            sentimentCounts.get(sentiment)[0]++;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("topic", topic);
        response.put("tweets", sampleTweets);
        response.put("sentiment_summary", Map.of(
            "positive", sentimentCounts.get("Positive")[0],
            "negative", sentimentCounts.get("Negative")[0],
            "neutral", sentimentCounts.get("Neutral")[0],
            "total", sampleTweets.size()
        ));
        response.put("analyzed_tweet_count", sampleTweets.size());
        response.put("note", "Sample data shown because Twitter API is currently unavailable. Real data will be shown when API is accessible.");
        response.put("status", "sample_data");

        return response;
    }

    private String classifyAccountType(JsonNode userJson) {
        if (userJson.get("verified").asBoolean()) return "Official";
        String bio = userJson.get("description").asText("").toLowerCase();
        if (bio.contains("business") || bio.contains("company")) return "Business";
        return "Personal";
    }

    private String analyzeSentiment(String text) {
        try {
            // Clean the text to remove problematic Unicode characters
            String cleanedText = cleanTextForNLP(text);

            if (cleanedText.trim().isEmpty()) {
                return "Neutral"; // Default for empty/cleaned text
            }

            CoreDocument doc = new CoreDocument(cleanedText);
            stanfordCoreNLP.annotate(doc);

            if (!doc.sentences().isEmpty()) {
                return doc.sentences().get(0).sentiment();
            } else {
                return "Neutral"; // Default if no sentences detected
            }
        } catch (Exception e) {
            System.err.println("Error analyzing sentiment for text: " + text.substring(0, Math.min(50, text.length())) + "... Error: " + e.getMessage());
            return "Neutral"; // Default to neutral on analysis failure
        }
    }

    private String cleanTextForNLP(String text) {
        if (text == null) return "";

        // Remove or replace problematic Unicode characters
        return text
            .replaceAll("[\\u2066-\\u2069]", "") // Remove invisible directional marks
            .replaceAll("[\\u200B-\\u200F]", "") // Remove zero-width characters
            .replaceAll("[\\uFE0F]", "")        // Remove variation selectors
            .replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]", "") // Remove emoji sequences (basic)
            .replaceAll("\\s+", " ")            // Normalize whitespace
            .trim();
    }
}
