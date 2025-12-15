package site.meowcat.openlens.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database configuration using H2 (Local File Database)
 * No limits, no cloud configuration needed.
 */
public class DatabaseConfig {
    private static DatabaseConfig instance;
    private HikariDataSource dataSource;

    private DatabaseConfig() {
        initializeDataSource();
        initializeSchema();
    }

    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    private void initializeDataSource() {
        // Explicitly load H2 driver
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("H2 JDBC Driver not found", e);
        }

        HikariConfig hikariConfig = new HikariConfig();
        // Use a local file database named 'scraper_db' in the current directory
        hikariConfig.setJdbcUrl("jdbc:h2:./scraper_db;MODE=PostgreSQL");
        hikariConfig.setUsername("sa");
        hikariConfig.setPassword("");

        hikariConfig.setMaximumPoolSize(10);

        // Optimize for local file access
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(hikariConfig);
    }

    private void initializeSchema() {
        // Simplified schema for H2
        // Removed tsvector/full-text search triggers as we are doing Client-Side Search
        String createPagesTable = """
                CREATE TABLE IF NOT EXISTS pages (
                    id SERIAL PRIMARY KEY,
                    url TEXT UNIQUE NOT NULL,
                    title TEXT,
                    content TEXT,
                    scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """;

        String createIndexes = """
                CREATE INDEX IF NOT EXISTS idx_pages_url ON pages(url);
                """;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            stmt.execute(createPagesTable);
            stmt.execute(createIndexes);

            System.out.println("Database schema initialized successfully (H2 Local DB)");
        } catch (SQLException e) {
            System.err.println("Error initializing database schema: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
