# Quick Start Guide

## First Time Setup

1. **Configure your Neon database**:
   ```bash
   cd /home/deck/Documents/se-scraper
   cp config.properties.template config.properties
   # Edit config.properties with your Neon credentials
   ```

2. **Build the project**:
   ```bash
   mvn clean package
   ```

3. **Scrape some pages**:
   ```bash
   java -cp target/search-engine-1.0-SNAPSHOT-jar-with-dependencies.jar com.searchengine.scraper.ScraperMain
   ```

4. **Export to static JSON** (creates `frontend/search-data.json`):
   ```bash
   java -cp target/search-engine-1.0-SNAPSHOT-jar-with-dependencies.jar com.searchengine.export.StaticExporter
   ```

5. **Open the frontend**:
   - Open `frontend/index.html` in your browser
   - Search works entirely client-side!

## Workflow

Every time you want to add new content:
1. Add URLs to `urls.txt`
2. Run the scraper (step 3 above)
3. Run the exporter (step 4 above)
4. Refresh your browser

That's it! No server needed.
