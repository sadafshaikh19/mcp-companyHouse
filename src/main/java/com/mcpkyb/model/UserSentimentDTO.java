package com.mcpkyb.model;

public class UserSentimentDTO {
    private String handle;
    private String accountType;
    private int positive;
    private int negative;
    private int neutral;

    public UserSentimentDTO(String handle, String accountType) {
        this.handle = handle;
        this.accountType = accountType;
        this.positive = 0;
        this.negative = 0;
        this.neutral = 0;
    }

    // Getters and setters
    public String getHandle() { return handle; }
    public void setHandle(String handle) { this.handle = handle; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public int getPositive() { return positive; }
    public void setPositive(int positive) { this.positive = positive; }

    public int getNegative() { return negative; }
    public void setNegative(int negative) { this.negative = negative; }

    public int getNeutral() { return neutral; }
    public void setNeutral(int neutral) { this.neutral = neutral; }

    public int getTotal() { return positive + negative + neutral; }
}
