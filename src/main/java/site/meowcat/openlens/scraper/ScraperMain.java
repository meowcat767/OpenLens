package site.meowcat.openlens.scraper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import site.meowcat.openlens.export.StaticExporter;

/**
 * Main entry point for the web scraper
 */
public class ScraperMain {
    public static void main(String[] args) {
        String urlFile = args.length > 0 ? args[0] : "urls.txt";

        System.out.println("=== Search Engine Crawler ===");
        System.out.println("Reading seed URLs from: " + urlFile);

        // Queue for URLs to visit
        Queue<String> urlQueue = new LinkedList<>(loadUrls(urlFile));

        // Track visited URLs to avoid loops
        Set<String> visited = new HashSet<>(urlQueue);

        if (urlQueue.isEmpty()) {
            System.err.println("No URLs found in " + urlFile);
            System.exit(1);
        }

        System.out.println("Starting crawl with " + urlQueue.size() + " seed URLs");
        System.out.println("Press Ctrl+C to stop...\n");

        WebScraper scraper = new WebScraper();
        int pagesScraped = 0;

        // Continuous crawl loop
        while (!urlQueue.isEmpty()) {
            String url = urlQueue.poll();

            WebScraper.ScrapeResult result = scraper.scrapeUrl(url);

            if (result.success) {
                pagesScraped++;

                // Add new links to queue
                for (String newLink : result.discoveredLinks) {
                    if (!visited.contains(newLink)) {
                        visited.add(newLink);
                        urlQueue.add(newLink);
                        // Persist new URL to file
                        saveUrl(urlFile, newLink);
                    }
                }

                // Update search index immediately
                StaticExporter.export("frontend/search-data.js");

                // Commit and push every 5 pages to avoid spamming
                if (pagesScraped % 5 == 0) {
                    pushUpdatesToGit();
                }
            }

            // Print progress every 10 pages
            if (pagesScraped % 10 == 0) {
                System.out.println("--- Stats: Scraped " + pagesScraped + " | Queue " + urlQueue.size() + " | Visited "
                        + visited.size() + " ---");
            }

            // Be polite - add delay
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("\n=== Crawl Complete ===");
        System.out.println("Total pages scraped: " + pagesScraped);
        scraper.printStats();
    }

    private static void pushUpdatesToGit() {
        try {
            System.out.println(">> Committing and pushing updates to Git...");

            // 1. Git Pull - Sync changes from remote first
            new ProcessBuilder("git", "pull")
                    .directory(new java.io.File("."))
                    .inheritIO()
                    .start().waitFor();

            // 2. Git Add
            new ProcessBuilder("git", "add", "frontend/search-data.js", "urls.txt", "blacklist.txt")
                    .directory(new java.io.File("."))
                    .inheritIO()
                    .start().waitFor();

            // 3. Git Commit
            new ProcessBuilder("git", "commit", "-m", "Auto-update search index & discovered URLs [Bot]")
                    .directory(new java.io.File("."))
                    .inheritIO()
                    .start().waitFor();

            // 4. Git Push
            Process p = new ProcessBuilder("git", "push")
                    .directory(new java.io.File("."))
                    .inheritIO()
                    .start();
            int exitCode = p.waitFor();

            if (exitCode == 0) {
                System.out.println(">> ✓ Successfully pushed to remote repository");
            } else {
                System.err.println(">> ⚠ Git push failed (Exit code: " + exitCode + ")");
            }

        } catch (Exception e) {
            System.err.println(">> ⚠ Git operation failed: " + e.getMessage());
        }
    }

    private static void saveUrl(String filename, String url) {
        try (java.io.FileWriter writer = new java.io.FileWriter(filename, true)) {
            writer.write(url + "\n");
        } catch (IOException e) {
            System.err.println("Error saving URL to " + filename + ": " + e.getMessage());
        }
    }

    private static List<String> loadUrls(String filename) {
        List<String> urls = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    urls.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading " + filename + ": " + e.getMessage());
        }

        return urls;
    }
}
