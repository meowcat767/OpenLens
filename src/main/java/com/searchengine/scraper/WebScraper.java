package com.searchengine.scraper;

import com.searchengine.config.DatabaseConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Web scraper that fetches pages and stores them in the database
 */
public class WebScraper {
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; SearchEngineBot/1.0)";

    private final DatabaseConfig dbConfig;

    public WebScraper() {
        this.dbConfig = DatabaseConfig.getInstance();
    }

    /**
     * Scrape a single URL and store it in the database
     */
    public boolean scrapeUrl(String url) {
        System.out.println("Scraping: " + url);

        try {
            // Fetch the page
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            // Extract title and content
            String title = doc.title();
            String content = extractContent(doc);

            // Store in database
            storeInDatabase(url, title, content);

            System.out.println("✓ Successfully scraped: " + title);
            return true;

        } catch (IOException e) {
            System.err.println("✗ Error fetching " + url + ": " + e.getMessage());
            return false;
        } catch (SQLException e) {
            System.err.println("✗ Database error for " + url + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Extract meaningful text content from the document
     */
    private String extractContent(Document doc) {
        // Remove script and style elements
        doc.select("script, style, nav, footer, header").remove();

        // Get text from body
        String content = doc.body().text();

        // Limit content length to avoid huge text blocks
        if (content.length() > 50000) {
            content = content.substring(0, 50000);
        }

        return content;
    }

    /**
     * Store the scraped page in the database
     */
    private void storeInDatabase(String url, String title, String content) throws SQLException {
        String sql = """
                INSERT INTO pages (url, title, content, scraped_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (url) DO UPDATE
                SET title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    scraped_at = CURRENT_TIMESTAMP
                """;

        try (Connection conn = dbConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, url);
            stmt.setString(2, title);
            stmt.setString(3, content);

            stmt.executeUpdate();
        }
    }

    /**
     * Get statistics about scraped pages
     */
    public void printStats() {
        String sql = "SELECT COUNT(*) as total FROM pages";

        try (Connection conn = dbConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                var rs = stmt.executeQuery()) {

            if (rs.next()) {
                int total = rs.getInt("total");
                System.out.println("\n=== Scraping Statistics ===");
                System.out.println("Total pages in database: " + total);
            }
        } catch (SQLException e) {
            System.err.println("Error getting stats: " + e.getMessage());
        }
    }
}
