// Static search - no backend required
// Data loaded from search-data.js into window.searchData

// DOM Elements
const searchForm = document.getElementById('searchForm');
const searchInput = document.getElementById('searchInput');
const resultsContainer = document.getElementById('resultsContainer');
const loadingIndicator = document.getElementById('loadingIndicator');
const errorMessage = document.getElementById('errorMessage');
const errorText = document.getElementById('errorText');
const statsElement = document.getElementById('stats');
const tabWeb = document.getElementById('tabWeb');
const tabImages = document.getElementById('tabImages');
const imageResultsContainer = document.getElementById('imageResultsContainer');

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
if (tabWeb && tabImages) {
    tabWeb.addEventListener('click', () => switchTab('web'));
    tabImages.addEventListener('click', () => switchTab('images'));
} else {
    console.error('Tab elements not found in DOM');
}

function switchTab(mode) {
    console.log('Switching tab to:', mode);
    currentMode = mode;

    // UI Updates
    if (mode === 'web') {
        if (tabWeb) tabWeb.classList.add('active');
        if (tabImages) tabImages.classList.remove('active');
        if (resultsContainer) resultsContainer.style.display = 'block';
        if (imageResultsContainer) imageResultsContainer.style.display = 'none';
    } else {
        if (tabWeb) tabWeb.classList.remove('active');
        if (tabImages) tabImages.classList.add('active');
        if (resultsContainer) resultsContainer.style.display = 'none';
        if (imageResultsContainer) imageResultsContainer.style.display = 'grid'; // Grid for images
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
        const contentLower = (page.content || '').toLowerCase();

        // Calculate relevance score
        let score = 0;
        let matchedTerms = 0;

        for (const term of queryTerms) {
            // Title matches are worth more
            const titleMatches = (titleLower.match(new RegExp(term, 'g')) || []).length;
            const contentMatches = (contentLower.match(new RegExp(term, 'g')) || []).length;

            if (titleMatches > 0 || contentMatches > 0) {
                matchedTerms++;
                score += titleMatches * 10 + contentMatches;
            }
        }

        // Only include if all terms matched
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
        const titleLower = (img.pageTitle || '').toLowerCase();

        let score = 0;
        let matchedTerms = 0;

        for (const term of queryTerms) {
            const altMatches = (altLower.match(new RegExp(term, 'g')) || []).length;
            const titleMatches = (titleLower.match(new RegExp(term, 'g')) || []).length;

            if (altMatches > 0 || titleMatches > 0) {
                matchedTerms++;
                score += altMatches * 10 + titleMatches;
            }
        }

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
        div.innerHTML = `
            <a href="${escapeHtml(result.img.pageUrl)}" target="_blank">
                <img src="${escapeHtml(result.img.src)}" alt="${escapeHtml(result.img.alt)}" loading="lazy">
                <div class="image-info">${escapeHtml(result.img.alt || result.img.pageTitle)}</div>
            </a>
        `;
        imageResultsContainer.appendChild(div);
    });
}

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

