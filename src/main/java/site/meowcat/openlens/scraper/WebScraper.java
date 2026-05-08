package site.meowcat.openlens.scraper;

import site.meowcat.openlens.config.DatabaseConfig;
import site.meowcat.openlens.util.PdfExtractor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Web scraper that fetches pages and stores them in the database
 */
public class WebScraper {

    private static final int TIMEOUT_MS = 60000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final Set<String> blacklist = new HashSet<>();
    private final DatabaseConfig dbConfig;

    public WebScraper() {
        this.dbConfig = DatabaseConfig.getInstance();
        loadBlacklist();
    }

    private void loadBlacklist() {
        try (java.io.BufferedReader reader =
                     new java.io.BufferedReader(new java.io.FileReader("blacklist.txt"))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty()) blacklist.add(line);
            }

        } catch (IOException e) {
            System.err.println("Warning: Could not load blacklist.txt: " + e.getMessage());
        }
    }

    /**
     * Scrape a single URL
     */
    public ScrapeResult scrapeUrl(String url) {
        System.out.println("Checking: " + url);

        if (!shouldScrape(url)) {
            return new ScrapeResult(true, Collections.emptySet());
        }

        String urlMatch = getBlacklistedTerm(url);
        if (urlMatch != null) {
            System.out.println("✗ Skipped (URL blacklisted by '" + urlMatch + "')");
            return new ScrapeResult(false, Collections.emptySet());
        }

        try {

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)
                    .execute();

            String contentType = response.contentType();

            String title = "";
            String content = "";
            Set<String> links = new HashSet<>();

            // =========================
            // PDF HANDLING
            // =========================
            if (contentType != null && contentType.contains("application/pdf")) {

                System.out.println("   > Detected PDF");

                content = PdfExtractor.extractText(url);
                title = url;

            } else {

                // =========================
                // HTML HANDLING
                // =========================
                Document doc = response.parse();

                title = doc.title();

                String titleMatch = getBlacklistedTerm(title);
                if (titleMatch != null) {
                    System.out.println("✗ Skipped (Title blacklisted by '" + titleMatch + "')");
                    return new ScrapeResult(false, Collections.emptySet());
                }

                content = extractContent(doc);

                String contentMatch = getBlacklistedTerm(content);
                if (contentMatch != null) {
                    System.out.println("✗ Skipped (Content blacklisted)");
                    return new ScrapeResult(false, Collections.emptySet());
                }

                links = extractLinks(doc, url);
                storeImages(url, doc);
            }

            storeInDatabase(url, title, content);

            System.out.println("✓ Indexed: " + title + " (" + links.size() + " links)");
            return new ScrapeResult(true, links);

        } catch (IOException e) {
            return new ScrapeResult(false, Collections.emptySet(), e.getMessage());
        } catch (SQLException e) {
            return new ScrapeResult(false, Collections.emptySet(), "Database error: " + e.getMessage());
        }
    }

    // =========================
    // BLACKLIST
    // =========================

    private String getBlacklistedTerm(String text) {
        if (text == null) return null;

        String lower = text.toLowerCase();

        for (String bad : blacklist) {
            if (lower.matches(".*\\b" + java.util.regex.Pattern.quote(bad) + "\\b.*")) {
                return bad;
            }
        }
        return null;
    }

    // =========================
    // LINKS
    // =========================

    private Set<String> extractLinks(Document doc, String baseUrl) {
        Set<String> links = new HashSet<>();
        Elements elements = doc.select("a[href]");

        for (Element el : elements) {
            String link = el.attr("abs:href");
            if (isValidLink(link) && getBlacklistedTerm(link) == null) {
                links.add(link);
            }
        }
        return links;
    }

    private boolean isValidLink(String url) {
        return url != null &&
                (url.startsWith("http://") || url.startsWith("https://"));
    }

    // =========================
    // CONTENT
    // =========================

    private String extractContent(Document doc) {
        doc.select("script, style, nav, footer, header").remove();

        String content = doc.body().text();

        if (content.length() > 50000) {
            content = content.substring(0, 50000);
        }

        return content;
    }

    // =========================
    // DATABASE
    // =========================

    private void storeInDatabase(String url, String title, String content)
            throws SQLException {

        String sql = """
                MERGE INTO pages (url, title, content, scraped_at)
                KEY (url)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (java.sql.Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, url);
            stmt.setString(2, title);
            stmt.setString(3, content);

            stmt.executeUpdate();

            // Reset error count on success
            try (PreparedStatement resetStmt = conn.prepareStatement(
                    "UPDATE pages SET error_count = 0, last_error = NULL WHERE url = ?")) {
                resetStmt.setString(1, url);
                resetStmt.executeUpdate();
            }
        }
    }

    private void storeImages(String pageUrl, Document doc) {
        try {
            Elements images = doc.select("img[src]");

            System.out.println("   > Found " + images.size() + " images");

            try (java.sql.Connection conn = dbConfig.getConnection();
                 PreparedStatement del = conn.prepareStatement(
                         "DELETE FROM images WHERE page_url = ?")) {

                del.setString(1, pageUrl);
                del.executeUpdate();
            }

            String sql = "INSERT INTO images (src, alt, page_url) VALUES (?, ?, ?)";

            try (java.sql.Connection conn = dbConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                for (Element img : images) {

                    String src = img.attr("abs:src");
                    String alt = img.attr("alt");

                    if (isValidImage(src)) {
                        stmt.setString(1, src);
                        stmt.setString(2, alt != null && alt.length() > 255
                                ? alt.substring(0, 255)
                                : alt);
                        stmt.setString(3, pageUrl);
                        stmt.addBatch();
                    }
                }

                stmt.executeBatch();
            }

        } catch (Exception e) {
            System.err.println("Error storing images: " + e.getMessage());
        }
    }

    private boolean isValidImage(String src) {
        return src != null &&
                (src.startsWith("http://") || src.startsWith("https://")) &&
                !src.contains("pixel") &&
                !src.contains("analytics");
    }

    // scraping stuff

    public void queueUrl(String url) {
        if (!isValidLink(url) || getBlacklistedTerm(url) != null) return;

        String sql = "INSERT INTO pages (url, scraped_at) VALUES (?, NULL)";

        try (java.sql.Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, url);
            stmt.executeUpdate();

        } catch (SQLException e) {
            if (!e.getSQLState().startsWith("23")) {
                System.err.println("Queue error: " + e.getMessage());
            }
        }
    }

    public String getNextUrlToScrape() {
        // Selection strategy:
        // 1. Never-scraped URLs with low error count first
        // 2. Then, failed URLs that have waited at least 1 hour (backoff)
        // 3. Finally, successfully scraped URLs older than 7 days
        String sql = """
            SELECT url FROM pages
            WHERE (scraped_at IS NULL AND error_count < 3)
               OR (scraped_at < CURRENT_TIMESTAMP - INTERVAL '1' HOUR AND error_count > 0 AND error_count < 3)
               OR (scraped_at < CURRENT_TIMESTAMP - INTERVAL '7' DAY AND error_count = 0)
            ORDER BY 
                CASE WHEN scraped_at IS NULL THEN 0 ELSE 1 END,
                error_count ASC,
                scraped_at ASC
            LIMIT 1
            """;

        try (java.sql.Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) return rs.getString("url");

        } catch (SQLException e) {
            System.err.println("Error getting next URL: " + e.getMessage());
        }

        return null;
    }

    public void markAsFailed(String url, String errorMessage) {
        String sql = """
                UPDATE pages 
                SET last_error = ?, 
                    error_count = error_count + 1,
                    scraped_at = CURRENT_TIMESTAMP
                WHERE url = ?
                """;

        try (java.sql.Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, errorMessage);
            stmt.setString(2, url);
            stmt.executeUpdate();

            System.out.println("✗ Marked " + url + " as failed (" + errorMessage + ")");

        } catch (SQLException e) {
            System.err.println("Error marking URL as failed: " + e.getMessage());
        }
    }

    public void printStats() {
        String sql = "SELECT COUNT(*) AS total FROM pages";

        try (java.sql.Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                System.out.println("Total pages: " + rs.getInt("total"));
            }

        } catch (SQLException e) {
            System.err.println("Stats error: " + e.getMessage());
        }
    }



    // =========================
    // SCHEDULING
    // =========================

    private boolean shouldScrape(String url) {
        String sql = "SELECT scraped_at FROM pages WHERE url = ?";

        try (java.sql.Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, url);

            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("scraped_at");

                    if (ts != null) {
                        long age = System.currentTimeMillis() - ts.getTime();
                        long sevenDays = 7L * 24 * 60 * 60 * 1000;

                        if (age < sevenDays) {
                            System.out.println("   > Skipped (recently scraped)");
                            return false;
                        }
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Warning: DB check failed, scraping anyway: " + e.getMessage());
        }

        return true;
    }

    // =========================
    // RESULT
    // =========================

    public static class ScrapeResult {
        public final boolean success;
        public final Set<String> discoveredLinks;
        public final String failureReason;

        public ScrapeResult(boolean success, Set<String> discoveredLinks) {
            this(success, discoveredLinks, null);
        }

        public ScrapeResult(boolean success, Set<String> discoveredLinks, String failureReason) {
            this.success = success;
            this.discoveredLinks = discoveredLinks;
            this.failureReason = failureReason;
        }
    }
}
