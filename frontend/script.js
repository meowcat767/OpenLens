// DOM Elements
const searchForm = document.getElementById('searchForm');
// ... other elements ...
const themeToggle = document.getElementById('themeToggle');

// Theme Logic
const savedTheme = localStorage.getItem('theme');
const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

if (savedTheme === 'dark' || (!savedTheme && systemDark)) {
    document.body.classList.add('dark-mode');
    if (themeToggle) themeToggle.textContent = '☀️';
}

if (themeToggle) {
    themeToggle.addEventListener('click', () => {
        document.body.classList.toggle('dark-mode');
        const isDark = document.body.classList.contains('dark-mode');
        themeToggle.textContent = isDark ? '☀️' : '🌙';
        localStorage.setItem('theme', isDark ? 'dark' : 'light');
    });
}

const searchInput = document.getElementById('searchInput');
const resultsContainer = document.getElementById('resultsContainer');
const loadingIndicator = document.getElementById('loadingIndicator');
const errorMessage = document.getElementById('errorMessage');
const errorText = document.getElementById('errorText');
const statsElement = document.getElementById('stats');
const tabWeb = document.getElementById('tabWeb');
const tabImages = document.getElementById('tabImages');
const tabMap = document.getElementById('tabMap');
const imageResultsContainer = document.getElementById('imageResultsContainer');
const mapContainer = document.getElementById('mapContainer');
const resetMapBtn = document.getElementById('resetMap');
const sitemapEl = document.getElementById('sitemap');

let currentMode = 'web'; // Default mode

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    console.log('OpenLens Script Loaded');

    // Check if data loaded correctly
    if (window.searchData) {
        const pageText = window.searchData.length === 1 ? 'page' : 'pages';
        const imageCount = window.imageData ? window.imageData.length : 0;
        if (statsElement) {
            statsElement.textContent = `${window.searchData.length} ${pageText} indexed | ${imageCount} images`;
        }

        // Fetch and display commit count
        fetchCommitCount();
    } else {
        console.warn('window.searchData is missing');
        showError('Could not load search data. Run the exporter: java -cp target/search-engine-1.0-SNAPSHOT-jar-with-dependencies.jar com.searchengine.export.StaticExporter');
    }
});

// Tab Handling
if (tabWeb && tabImages && tabMap) {
    tabWeb.addEventListener('click', () => switchTab('web'));
    tabImages.addEventListener('click', () => switchTab('images'));
    tabMap.addEventListener('click', () => switchTab('map'));
} else {
    console.error('Tab elements not found in DOM');
}

if (resetMapBtn) {
    resetMapBtn.addEventListener('click', () => renderSitemap());
}

function switchTab(mode) {
    console.log('Switching tab to:', mode);
    currentMode = mode;

    // UI Updates
    if (mode === 'web') {
        if (tabWeb) tabWeb.classList.add('active');
        if (tabImages) tabImages.classList.remove('active');
        if (tabMap) tabMap.classList.remove('active');
        if (resultsContainer) resultsContainer.style.display = 'block';
        if (imageResultsContainer) imageResultsContainer.style.display = 'none';
        if (mapContainer) mapContainer.style.display = 'none';
    } else if (mode === 'images') {
        if (tabWeb) tabWeb.classList.remove('active');
        if (tabImages) tabImages.classList.add('active');
        if (tabMap) tabMap.classList.remove('active');
        if (resultsContainer) resultsContainer.style.display = 'none';
        if (imageResultsContainer) imageResultsContainer.style.display = 'grid';
        if (mapContainer) mapContainer.style.display = 'none';
    } else if (mode === 'map') {
        if (tabWeb) tabWeb.classList.remove('active');
        if (tabImages) tabImages.classList.remove('active');
        if (tabMap) tabMap.classList.add('active');
        if (resultsContainer) resultsContainer.style.display = 'none';
        if (imageResultsContainer) imageResultsContainer.style.display = 'none';
        if (mapContainer) mapContainer.style.display = 'block';
        renderSitemap();
    }

    // Re-run search if query exists
    if (searchInput && searchInput.value.trim()) {
        handleSearch();
    }
}

// Fetch commit count from GitHub API
async function fetchCommitCount() {
    try {
        // We request 1 item per page to get the last page number from headers
        const response = await fetch('https://api.github.com/repos/meowcat767/OpenLens/commits?per_page=1');

        if (!response.ok) return;

        // The 'Link' header contains the URL for the last page:
        // <...page=123>; rel="last"
        const linkHeader = response.headers.get('Link');
        let commitCount = '?';

        if (linkHeader) {
            const match = linkHeader.match(/&page=(\d+)>; rel="last"/);
            if (match) {
                commitCount = match[1];
            }
        } else {
            // Fallback if only 1 page (rare for active repo but possible)
            const data = await response.json();
            commitCount = data.length;
        }

        if (statsElement) {
            const currentText = statsElement.textContent;
            statsElement.innerHTML = `${currentText} &nbsp;|&nbsp; <a href="https://github.com/meowcat767/OpenLens/commits/master" target="_blank" style="color: inherit; text-decoration: none;">${commitCount} Commits</a>`;
        }

    } catch (e) {
        console.error('Failed to fetch commit count:', e);
    }
}

// Handle search form submission
if (searchForm) {
    searchForm.addEventListener('submit', (e) => {
        e.preventDefault();
        handleSearch();
    });
} else {
    console.error('Search form not found');
}


// Main search handler
function handleSearch() {
    const query = searchInput.value.trim();

    if (!query) {
        // showError('Please enter a search query');
        return;
    }

    hideError();

    // Show tabs on first search
    if (document.querySelector('.search-tabs')) {
        document.querySelector('.search-tabs').style.display = 'block';
    }

    if (currentMode === 'web') {
        const results = searchPages(query);
        displayResults(query, results);
    } else {
        const results = searchImages(query);
        displayImageResults(query, results);
    }
}

// Client-side search implementation
function searchPages(query) {
    const queryTerms = query.toLowerCase().split(/\s+/);
    const results = [];

    if (!window.searchData) return [];

    for (const page of window.searchData) {
        const titleLower = (page.title || '').toLowerCase();
        // Check URL for keywords too
        const urlLower = (page.url || '').toLowerCase();

        // Calculate relevance score
        let score = 0;
        let matchedTerms = 0;

        for (const term of queryTerms) {
            const escapedTerm = escapeRegex(term);
            const titleMatches = (titleLower.match(new RegExp(escapedTerm, 'g')) || []).length;
            const urlMatches = (urlLower.match(new RegExp(escapedTerm, 'g')) || []).length;

            if (titleMatches > 0 || urlMatches > 0) {
                matchedTerms++;
                // Title matches are worth more
                score += titleMatches * 10 + urlMatches * 5;
            }
        }

        // Only include if all terms matched in Title or URL
        if (matchedTerms === queryTerms.length && score > 0) {
            results.push({
                page: page,
                score: score,
                snippet: generateSnippet(page.content, queryTerms)
            });
        }
    }

    // Sort by relevance score
    results.sort((a, b) => b.score - a.score);

    return results;
}

// Client-side image search
function searchImages(query) {
    const queryTerms = query.toLowerCase().split(/\s+/);
    const results = [];

    if (!window.imageData) return [];

    for (const img of window.imageData) {
        const altLower = (img.alt || '').toLowerCase();
        // Also check filename/src as it might contain keywords (e.g. cat.jpg)
        const srcLower = (img.src || '').toLowerCase();

        let score = 0;
        let matchedTerms = 0;

        for (const term of queryTerms) {
            const altMatches = (altLower.match(new RegExp(escapeRegex(term), 'g')) || []).length;
            const srcMatches = (srcLower.match(new RegExp(escapeRegex(term), 'g')) || []).length;

            if (altMatches > 0 || srcMatches > 0) {
                matchedTerms++;
                // Alt matches are worth more than src matches
                score += altMatches * 10 + srcMatches * 2;
            }
        }

        // Strict matching: must match all terms found in query
        if (matchedTerms === queryTerms.length && score > 0) {
            results.push({
                img: img,
                score: score
            });
        }
    }

    results.sort((a, b) => b.score - a.score);
    return results;
}

// Generate a snippet with highlighted query terms
function generateSnippet(content, queryTerms) {
    if (!content) return 'No preview available';

    // Find the first occurrence of any query term
    const lowerContent = content.toLowerCase();
    let bestPos = -1;

    for (const term of queryTerms) {
        const pos = lowerContent.indexOf(term);
        if (pos !== -1 && (bestPos === -1 || pos < bestPos)) {
            bestPos = pos;
        }
    }

    if (bestPos === -1) {
        return content.substring(0, 200) + '...';
    }

    // Extract snippet around the match
    const start = Math.max(0, bestPos - 100);
    const end = Math.min(content.length, bestPos + 200);
    let snippet = content.substring(start, end);

    if (start > 0) snippet = '...' + snippet;
    if (end < content.length) snippet = snippet + '...';

    // Highlight query terms
    for (const term of queryTerms) {
        const regex = new RegExp(`(${escapeRegex(term)})`, 'gi');
        snippet = snippet.replace(regex, '<b>$1</b>');
    }

    return snippet;
}

// Display search results
function displayResults(query, results) {
    resultsContainer.innerHTML = '';

    // Update stats
    updateStats(results.length, query);

    if (results.length === 0) {
        resultsContainer.innerHTML = `
            <div class="no-results">
                <h2>No results found</h2>
                <p>Try different keywords.</p>
            </div>
        `;
        return;
    }

    // Create result items
    results.forEach((result, index) => {
        const resultElement = createResultElement(result.page, result.snippet);
        resultsContainer.appendChild(resultElement);
    });
}

function displayImageResults(query, results) {
    imageResultsContainer.innerHTML = '';
    imageResultsContainer.className = 'image-grid';

    updateStats(results.length, query);

    if (results.length === 0) {
        imageResultsContainer.innerHTML = `
            <div class="no-results" style="grid-column: 1/-1">
                <h2>No image results found</h2>
            </div>
        `;
        return;
    }

    results.forEach(result => {
        const div = document.createElement('div');
        div.className = 'image-item';
        // Note: Removing the <a> wrapper and handling click via JS for Lightbox
        div.innerHTML = `
            <img src="${escapeHtml(result.img.src)}" 
                 alt="${escapeHtml(result.img.alt)}" 
                 loading="lazy"
                 data-page-url="${escapeHtml(result.img.pageUrl)}"
                 data-full-alt="${escapeHtml(result.img.alt || result.img.pageTitle)}">
            <div class="image-info">${escapeHtml(result.img.alt || result.img.pageTitle)}</div>
        `;

        // Add click listener for lightbox
        div.querySelector('img').addEventListener('click', (e) => {
            openLightbox(e.target.src, e.target.getAttribute('data-full-alt'), e.target.getAttribute('data-page-url'));
        });

        imageResultsContainer.appendChild(div);
    });
}

// Lightbox Logic
function openLightbox(src, caption, url) {
    lightbox.style.display = 'flex';
    lightboxImg.src = src;
    lightboxCaption.textContent = caption;
    lightboxLink.href = url;
}

if (lightboxClose) {
    lightboxClose.addEventListener('click', () => {
        lightbox.style.display = 'none';
    });
}

// Close on background click
if (lightbox) {
    lightbox.addEventListener('click', (e) => {
        if (e.target === lightbox) {
            lightbox.style.display = 'none';
        }
    });
}

// Close on Escape key
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && lightbox.style.display === 'flex') {
        lightbox.style.display = 'none';
    }
});

function updateStats(count, query) {
    const resultText = count === 1 ? 'result' : 'results';
    const baseText = `${count} ${resultText} for "${query}"`;

    // Keep the commit count if it's already there
    const currentHTML = statsElement.innerHTML;
    if (currentHTML.includes('|')) {
        const commitPart = currentHTML.split('|')[1];
        statsElement.innerHTML = `${baseText} &nbsp;|&nbsp;${commitPart}`;
    } else {
        statsElement.textContent = baseText;
    }
}

// Create a single result element
function createResultElement(page, snippet) {
    const div = document.createElement('div');
    div.className = 'result-item';

    div.innerHTML = `
        <h2 class="result-title">
            <a href="${escapeHtml(page.url)}" target="_blank" rel="noopener noreferrer">
                ${escapeHtml(page.title || 'Untitled')}
            </a>
        </h2>
        <div class="result-url">${escapeHtml(page.url)}</div>
        <div class="result-snippet">${snippet}</div>
    `;

    return div;
}

// UI Helper Functions
function showLoading() {
    loadingIndicator.style.display = 'block';
}

function hideLoading() {
    loadingIndicator.style.display = 'none';
}

function showError(message) {
    errorText.textContent = message;
    errorMessage.style.display = 'block';
}

function hideError() {
    errorMessage.style.display = 'none';
}

// Utility Functions
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function escapeRegex(text) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// ==========================================
// Sitemap Visualization (D3.js)
// ==========================================

function renderSitemap() {
    if (!window.searchData || !sitemapEl) return;
    
    // Clear previous
    sitemapEl.innerHTML = '';
    
    const data = window.searchData;
    const width = sitemapEl.clientWidth || 600;
    const height = 500;
    
    // 1. Convert flat data to hierarchy
    // We group by parentUrl
    const urlMap = {};
    data.forEach(p => {
        urlMap[p.url] = { ...p, children: [] };
    });
    
    const roots = [];
    data.forEach(p => {
        const node = urlMap[p.url];
        if (p.parentUrl && urlMap[p.parentUrl]) {
            urlMap[p.parentUrl].children.push(node);
        } else {
            roots.push(node);
        }
    });
    
    // Create a single virtual root if multiple actual roots exist
    let hierarchyData;
    if (roots.length === 1) {
        hierarchyData = roots[0];
    } else {
        hierarchyData = {
            title: "Roots",
            url: "seeds",
            children: roots
        };
    }
    
    const root = d3.hierarchy(hierarchyData);
    
    // 2. SVG Creation
    const svg = d3.select("#sitemap")
        .append("svg")
        .attr("width", width)
        .attr("height", height);
        
    const g = svg.append("g");
    
    // Zoom behavior
    const zoom = d3.zoom()
        .scaleExtent([0.1, 3])
        .on("zoom", (event) => {
            g.attr("transform", event.transform);
        });
        
    svg.call(zoom);
    
    // 3. Layout
    const treeLayout = d3.tree().size([width - 100, height - 150]);
    treeLayout(root);
    
    // 4. Draw Links
    g.selectAll(".link")
        .data(root.links())
        .enter()
        .append("path")
        .attr("class", "link")
        .attr("d", d3.linkVertical()
            .x(d => d.x)
            .y(d => d.y));
            
    // 5. Draw Nodes
    const node = g.selectAll(".node")
        .data(root.descendants())
        .enter()
        .append("g")
        .attr("class", "node")
        .attr("transform", d => `translate(${d.x},${d.y})`)
        .on("click", (event, d) => {
            if (d.data.url && d.data.url !== "seeds") {
                window.open(d.data.url, '_blank');
            }
        })
        .style("cursor", d => d.data.url === "seeds" ? "default" : "pointer");
        
    node.append("circle")
        .attr("r", 5);
        
    node.append("text")
        .attr("dy", ".35em")
        .attr("y", d => d.children ? -15 : 15)
        .style("text-anchor", "middle")
        .text(d => {
            let t = d.data.title || d.data.url || "Untitled";
            if (t.length > 20) t = t.substring(0, 17) + "...";
            return t;
        });

    // Initial center
    const initialScale = 0.8;
    const initialTranslateX = width / 10;
    const initialTranslateY = 50;
    svg.call(zoom.transform, d3.zoomIdentity.translate(initialTranslateX, initialTranslateY).scale(initialScale));
}
