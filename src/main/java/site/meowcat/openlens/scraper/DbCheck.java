package site.meowcat.openlens.scraper;

import site.meowcat.openlens.config.DatabaseConfig;
import java.sql.*;

public class DbCheck {
    public static void main(String[] args) throws Exception {
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        try (Connection conn = dbConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT url, parent_url FROM pages LIMIT 20")) {
            
            System.out.println("URL | Parent URL");
            System.out.println("----------------");
            while (rs.next()) {
                System.out.println(rs.getString("url") + " | " + rs.getString("parent_url"));
            }
        }
    }
}
