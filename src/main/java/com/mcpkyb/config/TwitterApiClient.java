package com.mcpkyb.config;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.*;

@Component
public class TwitterApiClient {

    private final OkHttpClient client;

    private final String bearerToken;

    private final int maxRetries = 3;
    private final ScheduledExecutorService scheduler;

    public TwitterApiClient(@Value("${twitter.api.bearer-token:#{null}}") String bearerToken) {
        this.client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .build();
        this.bearerToken = bearerToken;

        if (bearerToken == null || bearerToken.trim().isEmpty()) {
            System.err.println("WARNING: Twitter API bearer token is not configured!");
        } else {
            System.out.println("Twitter API client initialized with bearer token (length: " + bearerToken.length() + ")");
        }

        this.scheduler = Executors.newScheduledThreadPool(4);
    }

    public CompletableFuture<String> executeAsyncWithRateLimit(String url) {
        CompletableFuture<String> future = new CompletableFuture<>();
        executeAsync(url, future, 0, 1000);
        return future;
    }

    private void executeAsync(String url, CompletableFuture<String> future, int retries, int backoffMillis) {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearerToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.err.println("TwitterApiClient.onFailure called with exception: " + e.getClass().getName() + " - Message: '" + e.getMessage() + "'");
                String errorMsg = "Twitter API network error: " + (e.getMessage() != null ? e.getMessage() : "Unknown network error");
                System.err.println("Will throw error message: " + errorMsg);
                future.completeExceptionally(new IOException(errorMsg, e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.code() == 429) {
                        if (retries >= maxRetries) {
                            future.completeExceptionally(new IOException("Rate limit exceeded after retries"));
                            return;
                        }
                        long delay = calculateDelay(response, backoffMillis);
                        scheduler.schedule(() -> executeAsync(url, future, retries + 1, backoffMillis * 2), delay, TimeUnit.MILLISECONDS);
                        return;
                    }

                    if (!response.isSuccessful()) {
                        String errorBody = "";
                        try {
                            errorBody = response.body() != null ? response.body().string() : "No response body";
                        } catch (IOException e) {
                            errorBody = "Could not read error response: " + e.getMessage();
                        }
                        String errorMsg = "Twitter API HTTP error - Code: " + response.code() + ", Message: " + errorBody;
                        future.completeExceptionally(new IOException(errorMsg));
                        return;
                    }

                    future.complete(response.body().string());
                } catch (IOException e) {
                    future.completeExceptionally(e);
                } finally {
                    response.close();
                }
            }
        });
    }

    private long calculateDelay(Response response, int backoffMillis) {
        String resetHeader = response.header("x-rate-limit-reset");
        if (resetHeader != null) {
            long resetEpoch = Long.parseLong(resetHeader) * 1000;
            long waitTime = resetEpoch - System.currentTimeMillis();
            return Math.max(waitTime, backoffMillis);
        }
        return backoffMillis;
    }
}
