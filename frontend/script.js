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

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    // Check if data loaded correctly
    if (window.searchData) {
        const pageText = window.searchData.length === 1 ? 'page' : 'pages';
        statsElement.textContent = `${window.searchData.length} ${pageText} indexed`;

        // Fetch and display commit count
        fetchCommitCount();
    } else {
        showError('Could not load search data. Run the exporter: java -cp target/search-engine-1.0-SNAPSHOT-jar-with-dependencies.jar com.searchengine.export.StaticExporter');
    }
});

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

        const currentText = statsElement.textContent;
        statsElement.innerHTML = `${currentText} &nbsp;|&nbsp; <a href="https://github.com/meowcat767/OpenLens/commits/master" target="_blank" style="color: inherit; text-decoration: none;">${commitCount} Commits</a>`;

    } catch (e) {
        console.error('Failed to fetch commit count:', e);
    }
}

// Handle search form submission
searchForm.addEventListener('submit', (e) => {
    e.preventDefault();
    handleSearch();
});


// Main search handler
function handleSearch() {
    const query = searchInput.value.trim();

    if (!query) {
        showError('Please enter a search query');
        return;
    }

    hideError();

    // Perform client-side search
    const results = searchPages(query);
    displayResults(query, results);
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

    if (results.length === 0) {
        resultsContainer.innerHTML = `
            <div class="no-results">
                <h2>No results found</h2>
                <p>Try different keywords.</p>
            </div>
        `;
        statsElement.textContent = `No results for "${query}"`;
        return;
    }

    // Create result items
    results.forEach((result, index) => {
        const resultElement = createResultElement(result.page, result.snippet);
        resultsContainer.appendChild(resultElement);
    });

    // Update stats
    const resultCount = results.length;
    const resultText = resultCount === 1 ? 'result' : 'results';
    statsElement.textContent = `${resultCount} ${resultText} for "${query}"`;
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

