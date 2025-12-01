package com.mcpkyb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpkyb.utils.JsonLoader;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CustomerProfileAgent {

    @Autowired
    private OpenAiChatModel chatModel;

    public String getProfileSummary(String customerId) throws IOException {
        JsonNode crmData = JsonLoader.loadJson("crm.json");
        JsonNode customer = null;

        for (JsonNode c : crmData.get("customers")) {
            if (c.get("customer_id").asText().equals(customerId)) {
                customer = c;
                break;
            }
        }

        if (customer == null) {
            return "Customer not found.";
        }

        String prompt = String.format("""
            You are a KYB expert. Summarize this customer profile in one clean sentence:
            %s
            Include: legal name, onboarding year, sector, turnover band, and internal risk rating.
        """, customer.toString());

        // Build ChatRequest
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build();

        // Call chat() and extract AI response
        ChatResponse response = chatModel.chat(request);
        return response.aiMessage().text(); // or .content() depending on AiMessage API
    }
}