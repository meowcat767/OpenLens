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

        System.out.println("=== crawl-chan >~< ===");
        System.out.println("Initializing queue from seed file: " + urlFile);

        WebScraper scraper = new WebScraper();
        int pagesScraped = 0;

        // 1. Seed the database queue
        List<String> seedUrls = loadUrls(urlFile);
        for (String url : seedUrls) {
            scraper.queueUrl(url);
        }
        System.out.println("Seeded " + seedUrls.size() + " URLs into the database queue.");

        System.out.println("Starting crawl...");
        System.out.println("Press Ctrl+C to stop...\n");

        // Continuous crawl loop
        while (true) {
            String url = scraper.getNextUrlToScrape();

            if (url == null) {
                System.out.println("Queue empty or all pages scraped recently. Waiting 60s...");
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            WebScraper.ScrapeResult result = scraper.scrapeUrl(url);

            if (result.success) {
                pagesScraped++;

                // Add new links to queue
                for (String newLink : result.discoveredLinks) {
                    scraper.queueUrl(newLink);
                    // Also save to text file for backup/seed
                    saveUrl(urlFile, newLink);
                }

                // Update search index immediately
                StaticExporter.export("frontend/search-data.js");

                // Commit and push every 5 pages to avoid spamming
                if (pagesScraped % 5 == 0) {
                    pushUpdatesToGit();
                }
            }

            // Print progress every 10 pages
            if (pagesScraped % 10 == 0 && pagesScraped > 0) {
                System.out.println("\n--- Stats: Scraped " + pagesScraped + " in this session ---");
                scraper.printStats();
                System.out.println("----------------------------------------------\n");
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
        System.out.println("Total pages scraped in this session: " + pagesScraped);
        scraper.printStats();
    }

    private static void pushUpdatesToGit() {
        try {
            System.out.println(">> Committing and pushing updates to Git...");

            // 1. Git Add
            new ProcessBuilder("git", "add", "frontend/search-data.js", "urls.txt", "blacklist.txt")
                    .directory(new java.io.File("."))
                    .inheritIO()
                    .start().waitFor();

            // 2. Git Commit
            new ProcessBuilder("git", "commit", "-m", "Auto-update search index & discovered URLs [Bot]")
                    .directory(new java.io.File("."))
                    .inheritIO()
                    .start().waitFor();

            // 3. Git Pull (Rebase) - Sync changes from remote
            new ProcessBuilder("git", "pull", "--rebase")
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
        // We only append if it's not already in the file... but checking file is
        // expensive.
        // The original code just appended everywhere.
        // We can optimize this by maintaining a small cache or just letting it grow and
        // deduping later.
        // For now, let's keep original behavior but maybe check recent memory?
        // Actually, db is source of truth now, urls.txt is just a seed backup.
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
