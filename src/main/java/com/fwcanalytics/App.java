package com.fwcanalytics;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {
    public static void main(String[] args) {
        System.out.println("--- Fetching FIFA World Cup Data ---");
        String apiEndpoint = "https://worldcup26.ir/get/teams";
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiEndpoint)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response.body());
            System.out.println("Status Code: " + response.statusCode());
            if (rootNode.isArray()) {
                for (JsonNode team : rootNode) {
                    System.out.println("^| " + team.path("name").asText("Unknown"));
                }
            } else {
                System.out.println("API Connected. Content preview: " + response.body().substring(0, 100));
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
