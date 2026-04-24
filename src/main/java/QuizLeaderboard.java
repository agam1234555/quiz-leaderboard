import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class QuizLeaderboard {

    static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    static final String REG_NO = "RA2311003011642"; 
    static final ObjectMapper mapper = new ObjectMapper();
    static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {

        Set<String> seen = new HashSet<>();
        Map<String, Integer> scores = new HashMap<>();

        for (int poll = 0; poll <= 9; poll++) {

            System.out.println("\n========================================");
            System.out.println("Sending Poll #" + poll);
            System.out.println("========================================");

            String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + poll;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response: " + response.body());

            JsonNode root = mapper.readTree(response.body());
            JsonNode events = root.get("events");

            if (events != null && events.isArray()) {
                for (JsonNode event : events) {
                    String roundId     = event.get("roundId").asText();
                    String participant = event.get("participant").asText();
                    int    score       = event.get("score").asInt();

                    String uniqueKey = roundId + "_" + participant;

                    if (seen.contains(uniqueKey)) {
                        System.out.println("  DUPLICATE SKIPPED: " + uniqueKey);
                    } else {
                        seen.add(uniqueKey);
                        scores.merge(participant, score, Integer::sum);
                        System.out.println("  ADDED: " + participant
                                + " Round:" + roundId + " Score:+" + score);
                    }
                }
            }

            if (poll < 9) {
                System.out.println("Waiting 5 seconds...");
                Thread.sleep(5000);
            }
        }

        // Sort leaderboard by score descending
        List<Map.Entry<String, Integer>> leaderboard =
                new ArrayList<>(scores.entrySet());
        leaderboard.sort((a, b) -> b.getValue() - a.getValue());

        int grandTotal = 0;
        System.out.println("\n=== FINAL LEADERBOARD ===");
        for (Map.Entry<String, Integer> entry : leaderboard) {
            System.out.println(entry.getKey() + " --> " + entry.getValue());
            grandTotal += entry.getValue();
        }
        System.out.println("GRAND TOTAL: " + grandTotal);

        // Build submission JSON
        ObjectNode payload = mapper.createObjectNode();
        payload.put("regNo", REG_NO);

        ArrayNode leaderboardArray = mapper.createArrayNode();
        for (Map.Entry<String, Integer> entry : leaderboard) {
            ObjectNode item = mapper.createObjectNode();
            item.put("participant", entry.getKey());
            item.put("totalScore", entry.getValue());
            leaderboardArray.add(item);
        }
        payload.set("leaderboard", leaderboardArray);

        String jsonBody = mapper.writeValueAsString(payload);
        System.out.println("\nSubmitting: " + jsonBody);

        // POST submission
        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> submitResponse = client.send(
                submitRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println("\n=== SUBMISSION RESULT ===");
        System.out.println(submitResponse.body());
    }
}