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
    private static final int TIMEOUT_MS = 60000; // 60 seconds
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; SearchEngineBot/1.0)";
    private Set<String> blacklist = new HashSet<>();

    private final DatabaseConfig dbConfig;

    public WebScraper() {
        this.dbConfig = DatabaseConfig.getInstance();
        loadBlacklist();
    }

    private void loadBlacklist() {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("blacklist.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty()) {
                    blacklist.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load blacklist.txt: " + e.getMessage());
        }
    }

    /**
     * Scrape a single URL and return the result
     */
    public ScrapeResult scrapeUrl(String url) {
        System.out.println("Crawling: " + url);

        String urlMatch = getBlacklistedTerm(url);
        if (urlMatch != null) {
            System.out.println("✗ Skipped (URL blacklisted by '" + urlMatch + "'): " + url);
            return new ScrapeResult(false, Collections.emptySet());
        }

        try {
            // Fetch the page
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            // Extract title
            String title = doc.title();

            // Check title for blacklisted words
            String titleMatch = getBlacklistedTerm(title);
            if (titleMatch != null) {
                System.out.println("✗ Skipped (Title blacklisted by '" + titleMatch + "'): " + title);
                return new ScrapeResult(false, Collections.emptySet());
            }

            String content = extractContent(doc);

            // Check content for blacklisted words
            String contentMatch = getBlacklistedTerm(content);
            if (contentMatch != null) {
                System.out.println("✗ Skipped (Content blacklisted by '" + contentMatch + "')");
                return new ScrapeResult(false, Collections.emptySet());
            }

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

    // Returns the blacklisted term encountered, or null if none
    private String getBlacklistedTerm(String text) {
        if (text == null)
            return null;
        String lowerText = text.toLowerCase();

        for (String badWord : blacklist) {
            // Use regex for whole word matching to avoid "Sussex" matching "sex"
            // \b matches word boundaries
            if (lowerText.matches(".*\\b" + java.util.regex.Pattern.quote(badWord) + "\\b.*")) {
                return badWord;
            }
        }
        return null;
    }

    private Set<String> extractLinks(Document doc, String baseUrl) {
        Set<String> links = new HashSet<>();
        Elements elements = doc.select("a[href]");

        for (Element element : elements) {
            String link = element.attr("abs:href");
            // Basic validation and blacklist check
            if (isValidLink(link) && getBlacklistedTerm(link) == null) {
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
                MERGE INTO pages (url, title, content, scraped_at)
                KEY (url)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (Connection conn = dbConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, url);
            stmt.setString(2, title);
            stmt.setString(3, content);

            stmt.executeUpdate();
        }

        // Store images
        storeImages(url, content);
    }

    private void storeImages(String pageUrl, String htmlContent) {
        try {
            Document doc = Jsoup.parse(htmlContent, pageUrl);
            Elements images = doc.select("img[src]");

            String sql = "INSERT INTO images (src, alt, page_url) VALUES (?, ?, ?)";

            try (Connection conn = dbConfig.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                for (Element img : images) {
                    String src = img.attr("abs:src");
                    String alt = img.attr("alt");

                    if (isValidImage(src)) {
                        stmt.setString(1, src);
                        stmt.setString(2, alt.length() > 255 ? alt.substring(0, 255) : alt);
                        stmt.setString(3, pageUrl);
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }
        } catch (Exception e) {
            System.err.println("Error storing images for " + pageUrl + ": " + e.getMessage());
        }
    }

    private boolean isValidImage(String src) {
        return src != null &&
                (src.startsWith("http://") || src.startsWith("https://")) &&
                !src.contains("pixel") &&
                !src.contains("analytics");
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
