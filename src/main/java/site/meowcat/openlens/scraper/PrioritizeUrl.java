package site.meowcat.openlens.scraper;

import site.meowcat.openlens.config.DatabaseConfig;
import java.sql.*;

public class PrioritizeUrl {
    public static void main(String[] args) throws Exception {
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        try (Connection conn = dbConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Delete sub-pages of python.org to force re-discovery
            int deleted = stmt.executeUpdate("DELETE FROM pages WHERE url LIKE 'https://www.python.org/%' AND url != 'https://www.python.org/'");
            System.out.println("Deleted " + deleted + " python.org sub-pages.");
            
            // Prioritize python.org main page
            stmt.executeUpdate("UPDATE pages SET scraped_at = NULL, error_count = 0 WHERE url = 'https://www.python.org/'");
            
            // Mark everything else as recently scraped
            stmt.executeUpdate("UPDATE pages SET scraped_at = CURRENT_TIMESTAMP WHERE url != 'https://www.python.org/'");
        }
    }
}
