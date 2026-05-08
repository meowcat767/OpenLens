package site.meowcat.openlens.scraper;

import site.meowcat.openlens.config.DatabaseConfig;
import java.sql.*;

public class DbCheck {
    public static void main(String[] args) throws Exception {
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        try (Connection conn = dbConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM pages WHERE parent_url IS NOT NULL")) {
                if (rs.next()) {
                    System.out.println("Total pages with parent_url: " + rs.getInt(1));
                }
            }
            
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM pages")) {
                if (rs.next()) {
                    System.out.println("Total pages in database: " + rs.getInt(1));
                }
            }
        }
    }
}
