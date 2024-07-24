import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore requestSemaphore;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger requestCount;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestSemaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.requestCount = new AtomicInteger(0);

        long intervalMillis = timeUnit.toMillis(1);
        scheduler.scheduleAtFixedRate(this::resetRateLimit, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void resetRateLimit() {
        requestSemaphore.release(requestSemaphore.availablePermits());
        requestCount.set(0);
    }

    public void createDocument(Object document, String signature) throws IOException, InterruptedException {
        requestSemaphore.acquire();
        requestCount.incrementAndGet();

        String jsonBody = objectMapper.writeValueAsString(document);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + signature) // Assume the signature is used for Authorization
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to create document: " + response.body());
        }
    }

    public void close() {
        scheduler.shutdown();
    }

    public static void main(String[] args) {
        try {
            CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

            ObjectNode document = new ObjectMapper().createObjectNode();
            document.put("doc_id", "123");
            document.put("doc_status", "NEW");
            document.put("doc_type", "LP_INTRODUCE_GOODS");
            document.put("importRequest", true);
            document.put("owner_inn", "1234567890");
            document.put("participant_inn", "1234567890");
            document.put("producer_inn", "1234567890");
            document.put("production_date", "2024-01-01");
            document.put("production_type", "TYPE");

            String signature = "your-signature";

            api.createDocument(document, signature);

            api.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
