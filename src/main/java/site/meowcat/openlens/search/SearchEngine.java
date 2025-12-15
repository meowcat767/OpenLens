package site.meowcat.openlens.search;

import site.meowcat.openlens.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Search engine using PostgreSQL full-text search
 */
public class SearchEngine {
    private final DatabaseConfig dbConfig;

    public SearchEngine() {
        this.dbConfig = DatabaseConfig.getInstance();
    }

    /**
     * Search for pages matching the query
     */
    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Convert query to tsquery format
        String tsQuery = convertToTsQuery(query);

        String sql = """
                SELECT
                    id,
                    url,
                    title,
                    ts_rank(search_vector, to_tsquery('english', ?)) as rank,
                    ts_headline('english', content, to_tsquery('english', ?),
                        'MaxWords=30, MinWords=15, MaxFragments=1') as snippet
                FROM pages
                WHERE search_vector @@ to_tsquery('english', ?)
                ORDER BY rank DESC
                LIMIT ?
                """;

        List<SearchResult> results = new ArrayList<>();

        try (Connection conn = dbConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, tsQuery);
            stmt.setString(2, tsQuery);
            stmt.setString(3, tsQuery);
            stmt.setInt(4, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SearchResult result = new SearchResult(
                            rs.getInt("id"),
                            rs.getString("url"),
                            rs.getString("title"),
                            rs.getString("snippet"),
                            rs.getDouble("rank"));
                    results.add(result);
                }
            }
        } catch (SQLException e) {
            System.err.println("Search error: " + e.getMessage());
        }

        return results;
    }

    /**
     * Convert user query to PostgreSQL tsquery format
     */
    private String convertToTsQuery(String query) {
        // Split query into words and join with & (AND operator)
        String[] words = query.trim().split("\\s+");
        StringBuilder tsQuery = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                tsQuery.append(" & ");
            }
            // Escape single quotes and add prefix matching
            String word = words[i].replace("'", "''");
            tsQuery.append(word).append(":*");
        }

        return tsQuery.toString();
    }

    /**
     * Get database statistics
     */
    public DatabaseStats getStats() {
        String sql = """
                SELECT
                    COUNT(*) as total_pages,
                    MAX(scraped_at) as last_scraped
                FROM pages
                """;

        try (Connection conn = dbConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return new DatabaseStats(
                        rs.getInt("total_pages"),
                        rs.getTimestamp("last_scraped"));
            }
        } catch (SQLException e) {
            System.err.println("Stats error: " + e.getMessage());
        }

        return new DatabaseStats(0, null);
    }

    /**
     * Search result data class
     */
    public static class SearchResult {
        public final int id;
        public final String url;
        public final String title;
        public final String snippet;
        public final double rank;

        public SearchResult(int id, String url, String title, String snippet, double rank) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.snippet = snippet;
            this.rank = rank;
        }
    }

    /**
     * Database statistics data class
     */
    public static class DatabaseStats {
        public final int totalPages;
        public final java.sql.Timestamp lastScraped;

        public DatabaseStats(int totalPages, java.sql.Timestamp lastScraped) {
            this.totalPages = totalPages;
            this.lastScraped = lastScraped;
        }
    }
}
