package com.searchengine.scraper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the web scraper
 */
public class ScraperMain {
    public static void main(String[] args) {
        String urlFile = args.length > 0 ? args[0] : "urls.txt";

        System.out.println("=== Search Engine Scraper ===");
        System.out.println("Reading URLs from: " + urlFile);

        List<String> urls = loadUrls(urlFile);
        if (urls.isEmpty()) {
            System.err.println("No URLs found in " + urlFile);
            System.exit(1);
        }

        System.out.println("Found " + urls.size() + " URLs to scrape\n");

        WebScraper scraper = new WebScraper();
        int successful = 0;
        int failed = 0;

        for (String url : urls) {
            if (scraper.scrapeUrl(url)) {
                successful++;
            } else {
                failed++;
            }

            // Be polite - add a small delay between requests
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("\n=== Scraping Complete ===");
        System.out.println("Successful: " + successful);
        System.out.println("Failed: " + failed);

        scraper.printStats();
    }

    private static List<String> loadUrls(String filename) {
        List<String> urls = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
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
