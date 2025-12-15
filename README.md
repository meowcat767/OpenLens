# OpenLens- Static Version

A search engine with Java-based web scraper and **static frontend** that works without a backend server.

## How It Works

1. **Scraper** - Java program that crawls web pages and stores them in Neon PostgreSQL
2. **Exporter** - Exports database content to a JSON file
3. **Frontend** - Static HTML/CSS/JS that searches the JSON file client-side (no server needed!)

## Setup

### 1. Configure Database

```bash
cp config.properties.template config.properties
```

Edit `config.properties` with your Neon database connection string:
```properties
db.url=postgresql://YOUR_USER:YOUR_PASSWORD@YOUR_HOST/YOUR_DATABASE?sslmode=require
```

### 2. Build the Project

```bash
mvn clean package
```

### 3. Scrape Web Pages

```bash
java -cp target/search-engine-1.0-SNAPSHOT-jar-with-dependencies.jar \
  com.searchengine.scraper.ScraperMain
```

This reads URLs from `urls.txt` and stores content in the database.

### 4. Export to Static JSON

```bash
java -cp target/search-engine-1.0-SNAPSHOT-jar-with-dependencies.jar \
  com.searchengine.export.StaticExporter
```

This creates `frontend/search-data.json` with all indexed content.

### 5. Open the Frontend

Simply open `frontend/index.html` in your browser - **no server needed!**

The search runs entirely in your browser using JavaScript.

## Updating Content

When you scrape new pages:

1. Run the scraper again
2. Run the exporter again to update `search-data.json`
3. Refresh the browser page


## Search Algorithm

The client-side search:
1. Splits query into terms
2. Searches both title and content
3. Requires all terms to match (AND logic)
4. Scores results (title matches worth 10x content matches)
5. Sorts by relevance
6. Generates snippets with highlighted terms

