package com.mcpkyb.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;

public class JsonLoader {
    public static JsonNode loadJson(String fileName) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(new ClassPathResource(fileName).getInputStream());
    }
}