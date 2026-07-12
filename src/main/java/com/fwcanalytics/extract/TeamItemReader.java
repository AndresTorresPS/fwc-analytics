package com.fwcanalytics.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;

/**
 * Spring Batch ItemReader implementation dedicated to extracting Team Items
 * from a FIFA World Cup 2026 API.
 * <p>
 * This component connects to the specific teams REST endpoint, parses the JSON response,
 * and provides a sequential stream of {@link JsonNode} objects representing
 * individual teams for downstream processing in the ETL pipeline.
 * </p>
 *
 * @author Andres Torres
 * @version 1.0
 * @see org.springframework.batch.item.ItemReader
 */
@Component
public class TeamItemReader implements ItemReader<JsonNode> {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String teamsApiUrl;
    private final String jsonArrayNodeName;

    private Iterator<JsonNode> teamsIterator;
    private boolean isTeamsDataFetched = false;

    /**
     * Constructs a new {@code TeamItemReader} with necessary dependencies and configurations.
     * <p>
     * The URL components are injected via Spring properties to allow easy switching
     * between environments (e.g., dev, prod) without altering the compiled code.
     * </p>
     *
     * @param objectMapper          The Jackson mapper used to parse the HTTP JSON response.
     * @param baseUrl               The base URL of the FIFA API (injected from application properties).
     * @param jsonArrayNodeName     The JSON array node name
     * @param teamsEndpoint         The specific endpoint path for teams (injected from application properties).
     */
    public TeamItemReader(
            ObjectMapper objectMapper,
            @Value("${fwc.api.base-url:https://worldcup26.ir}") String baseUrl,
            @Value("${fwc.api.endpoints.teams:/get/teams}") String teamsEndpoint,
            @Value("${fwc.api.json-array-node-name: teams}") String jsonArrayNodeName){

        this.objectMapper = objectMapper;
        this.teamsApiUrl = baseUrl + teamsEndpoint;
        this.jsonArrayNodeName = jsonArrayNodeName;

        // Resilient HTTP client building with a connection timeout to prevent hanging threads
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Reads the next team {@link JsonNode} from the API response.
     * <p>
     * On the first invocation, this method triggers the HTTP call to fetch all teams
     * and initializes an internal iterator. Subsequent calls return the next team
     * from the iterator until all teams have been processed.
     * </p>
     *
     * @return The next {@link JsonNode} representing a team, or {@code null} if the data is exhausted.
     * @throws UnexpectedInputException      If the API response is fundamentally malformed.
     * @throws ParseException                If the JSON structure cannot be parsed correctly.
     * @throws NonTransientResourceException If the API resource is unavailable (e.g., HTTP 500).
     * @throws Exception                     If an unexpected I/O error occurs.
     */
    @Override
    public JsonNode read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (!isTeamsDataFetched) {
            fetchTeamsData();
        }

        if (teamsIterator != null && teamsIterator.hasNext()) {
            return teamsIterator.next();
        }

        // Returning null signals to Spring Batch that the data source is exhausted
        return null;
    }

    /**
     * Executes the HTTP GET request to the Teams API endpoint and prepares the iterator.
     * <p>
     * This method handles the network communication, validates the HTTP status code,
     * and extracts the specific JSON array node containing the teams payload.
     * </p>
     *
     * @throws NonTransientResourceException If the HTTP status is not 200 OK.
     * @throws ParseException                If the expected JSON array node (e.g., "teams") is missing or invalid.
     * @throws Exception                     If an I/O error or interruption occurs during the HTTP call.
     */
    private void fetchTeamsData() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(teamsApiUrl))
                .timeout(Duration.ofSeconds(15)) // Read timeout
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new NonTransientResourceException(
                    "Failed to retrieve teams data from API. HTTP Status: " + response.statusCode());
        }

        JsonNode rootNode = objectMapper.readTree(response.body());
        JsonNode teamsArray = rootNode.path(jsonArrayNodeName);

        if (teamsArray.isMissingNode() || !teamsArray.isArray()) {
            throw new ParseException(
                    "Invalid JSON structure: Expected an array node named '" + jsonArrayNodeName + "'");
        }

        this.teamsIterator = teamsArray.elements();
        this.isTeamsDataFetched = true;
    }
}