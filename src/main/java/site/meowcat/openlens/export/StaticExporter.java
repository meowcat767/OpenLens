package site.meowcat.openlens.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import site.meowcat.openlens.config.DatabaseConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Export database content to a static JSON file for client-side search
 */
public class StaticExporter {

    public static class PageData {
        public int id;
        public String url;
        public String title;
        public String content;
        public String scrapedAt;

        public PageData(int id, String url, String title, String content, String scrapedAt) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.content = content;
            this.scrapedAt = scrapedAt;
        }
    }

    public static class ImageData {
        public String src;
        public String alt;
        public String pageTitle;
        public String pageUrl;

        public ImageData(String src, String alt, String pageTitle, String pageUrl) {
            this.src = src;
            this.alt = alt;
            this.pageTitle = pageTitle;
            this.pageUrl = pageUrl;
        }
    }

    public static void main(String[] args) {
        String outputFile = args.length > 0 ? args[0] : "frontend/search-data.js";
        export(outputFile);
    }

    public static void export(String outputFile) {
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        List<PageData> pages = new ArrayList<>();
        List<ImageData> images = new ArrayList<>();

        String pageSql = "SELECT id, url, title, content, scraped_at FROM pages ORDER BY scraped_at DESC";
        String imageSql = """
                SELECT i.src, i.alt, p.title, p.url
                FROM images i
                JOIN pages p ON i.page_url = p.url
                ORDER BY p.scraped_at DESC
                """;

        try (Connection conn = dbConfig.getConnection()) {

            // 1. Export Pages
            try (PreparedStatement stmt = conn.prepareStatement(pageSql);
                    ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String content = rs.getString("content");
                    if (content != null && content.length() > 5000) {
                        content = content.substring(0, 5000);
                    }
                    pages.add(new PageData(
                            rs.getInt("id"),
                            rs.getString("url"),
                            rs.getString("title"),
                            content,
                            rs.getTimestamp("scraped_at").toString()));
                }
            }

            // 2. Export Images
            try (PreparedStatement stmt = conn.prepareStatement(imageSql);
                    ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    images.add(new ImageData(
                            rs.getString("src"),
                            rs.getString("alt"),
                            rs.getString("title"),
                            rs.getString("url")));
                }
            }

            // Write to JS file
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write("window.searchData = ");
                gson.toJson(pages, writer);
                writer.write(";\n\n");

                writer.write("window.imageData = ");
                gson.toJson(images, writer);
                writer.write(";");
            }

        } catch (SQLException | IOException e) {
            System.err.println("Error exporting data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
