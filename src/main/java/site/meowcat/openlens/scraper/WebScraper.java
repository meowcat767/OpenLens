package site.meowcat.openlens.scraper;

import site.meowcat.openlens.config.DatabaseConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
     * Scrape a single URL and return the result
     */
    public ScrapeResult scrapeUrl(String url) {
        System.out.println("Crawling: " + url);

        try {
            // Fetch the page
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            // Extract title, content, and links
            String title = doc.title();
            String content = extractContent(doc);
            Set<String> links = extractLinks(doc, url);

            // Store in database
            storeInDatabase(url, title, content);

            System.out.println("✓ Indexed: " + title + " (" + links.size() + " new links)");
            return new ScrapeResult(true, links);

        } catch (IOException e) {
            System.err.println("✗ Error fetching " + url + ": " + e.getMessage());
            return new ScrapeResult(false, Collections.emptySet());
        } catch (SQLException e) {
            System.err.println("✗ Database error for " + url + ": " + e.getMessage());
            return new ScrapeResult(false, Collections.emptySet());
        }
    }

    private Set<String> extractLinks(Document doc, String baseUrl) {
        Set<String> links = new HashSet<>();
        Elements elements = doc.select("a[href]");

        for (Element element : elements) {
            String link = element.attr("abs:href");
            // Basic validation to ensure useful http/https links
            if (isValidLink(link)) {
                links.add(link);
            }
        }
        return links;
    }

    private boolean isValidLink(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String extractContent(Document doc) {
        // Remove script and style elements
        doc.select("script, style, nav, footer, header").remove();

        // Get text from body
        String content = doc.body().text();

        // Limit content length
        if (content.length() > 50000) {
            content = content.substring(0, 50000);
        }

        return content;
    }

    public static class ScrapeResult {
        public final boolean success;
        public final Set<String> discoveredLinks;

        public ScrapeResult(boolean success, Set<String> discoveredLinks) {
            this.success = success;
            this.discoveredLinks = discoveredLinks;
        }
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
