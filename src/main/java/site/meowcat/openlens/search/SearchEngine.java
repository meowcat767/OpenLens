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
        List<SearchResult> results = new ArrayList<>();
        // Simple search for H2 (PostgreSQL FTS features removed)
        String sql = """
                SELECT url, title, content
                FROM pages
                WHERE LOWER(title) LIKE LOWER(?) OR LOWER(content) LIKE LOWER(?)
                LIMIT ?
                """;

        try (Connection conn = dbConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            String likeQuery = "%" + query + "%";
            stmt.setString(1, likeQuery);
            stmt.setString(2, likeQuery);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String url = rs.getString("url");
                    String title = rs.getString("title");
                    String content = rs.getString("content");
                    // Simple snippet generation
                    String snippet = content != null && content.length() > 200
                            ? content.substring(0, 200) + "..."
                            : content;

                    results.add(new SearchResult(url, title, snippet, 0)); // Rank 0 for simple search
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
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

        // Constructor for simple search (no ID needed)
        public SearchResult(String url, String title, String snippet, double rank) {
            this(0, url, title, snippet, rank);
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
