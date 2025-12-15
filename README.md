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

## Features

✅ **No backend server required** - completely static
✅ **Client-side search** - fast, instant results
✅ **Simple Google-like UI** - clean and minimal
✅ **Relevance ranking** - title matches weighted higher
✅ **Snippet generation** - shows context around matches
✅ **Highlighted terms** - query terms are bolded in results


## Search Algorithm

The client-side search:
1. Splits query into terms
2. Searches both title and content
3. Requires all terms to match (AND logic)
4. Scores results (title matches worth 10x content matches)
5. Sorts by relevance
6. Generates snippets with highlighted terms

## Advantages of Static Approach

- ✅ No server to run or maintain
- ✅ Can host on any static file server (GitHub Pages, Netlify, etc.)
- ✅ Instant search results (no network latency)
- ✅ Works offline once loaded
- ✅ Simple deployment

## Limitations

- JSON file size grows with content (consider limiting to ~1000 pages)
- Less sophisticated search than PostgreSQL full-text search
- Need to re-export when database changes
- All data loaded into browser memory

## Tips

- Keep content under 5000 characters per page (done automatically by exporter)
- Re-export regularly to keep search data fresh
- For large datasets (>1000 pages), consider keeping the API server approach
