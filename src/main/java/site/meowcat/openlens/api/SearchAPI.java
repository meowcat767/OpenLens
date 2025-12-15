package site.meowcat.openlens.api;

import site.meowcat.openlens.config.DatabaseConfig;
import site.meowcat.openlens.search.SearchEngine;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API server for the search engine
 */
public class SearchAPI {
    private final SearchEngine searchEngine;
    private final DatabaseConfig dbConfig;

    public SearchAPI() {
        this.dbConfig = DatabaseConfig.getInstance();
        this.searchEngine = new SearchEngine();
    }

    public void start() {
        int port = Integer.parseInt(dbConfig.getProperty("api.port"));
        String host = dbConfig.getProperty("api.host");

        Javalin app = Javalin.create(config -> {
            // Enable CORS for frontend
            config.plugins.enableCors(cors -> {
                cors.add(it -> {
                    it.anyHost();
                });
            });
        }).start(host, port);

        System.out.println("=== Search Engine API ===");
        System.out.println("Server started on http://" + host + ":" + port);
        System.out.println("\nEndpoints:");
        System.out.println("  GET /api/search?q=<query>  - Search for pages");
        System.out.println("  GET /api/stats             - Get database statistics");
        System.out.println();

        // Search endpoint
        app.get("/api/search", this::handleSearch);

        // Stats endpoint
        app.get("/api/stats", this::handleStats);

        // Health check
        app.get("/api/health", ctx -> {
            ctx.json(Map.of("status", "ok"));
        });

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down API server...");
            app.stop();
            dbConfig.close();
        }));
    }

    private void handleSearch(Context ctx) {
        String query = ctx.queryParam("q");

        if (query == null || query.trim().isEmpty()) {
            ctx.status(400).json(Map.of(
                    "error", "Query parameter 'q' is required"));
            return;
        }

        String limitParam = ctx.queryParam("limit");
        int limit = limitParam != null ? Integer.parseInt(limitParam) : 10;
        List<SearchEngine.SearchResult> results = searchEngine.search(query, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("count", results.size());
        response.put("results", results);

        ctx.json(response);
    }

    private void handleStats(Context ctx) {
        SearchEngine.DatabaseStats stats = searchEngine.getStats();

        Map<String, Object> response = new HashMap<>();
        response.put("totalPages", stats.totalPages);
        response.put("lastScraped", stats.lastScraped != null ? stats.lastScraped.toString() : null);

        ctx.json(response);
    }

    public static void main(String[] args) {
        SearchAPI api = new SearchAPI();
        api.start();
    }
}
