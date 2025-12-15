package com.searchengine.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Database configuration and connection pool management
 */
public class DatabaseConfig {
    private static DatabaseConfig instance;
    private HikariDataSource dataSource;
    private Properties config;

    private DatabaseConfig() {
        loadConfig();
        initializeDataSource();
        initializeSchema();
    }

    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    private void loadConfig() {
        config = new Properties();
        try {
            config.load(new FileInputStream("config.properties"));
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
            System.err.println("Please copy config.properties.template to config.properties and configure it.");
            System.exit(1);
        }
    }

    private void initializeDataSource() {
        // Explicitly load PostgreSQL driver
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC Driver not found: " + e.getMessage());
            throw new RuntimeException(e);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setJdbcUrl(config.getProperty("db.url"));

        // Set credentials if they exist in config
        if (config.containsKey("db.username")) {
            hikariConfig.setUsername(config.getProperty("db.username"));
        }
        if (config.containsKey("db.password")) {
            hikariConfig.setPassword(config.getProperty("db.password"));
        }

        hikariConfig.setMaximumPoolSize(Integer.parseInt(config.getProperty("db.pool.maxSize", "10")));
        hikariConfig.setMinimumIdle(Integer.parseInt(config.getProperty("db.pool.minIdle", "2")));
        hikariConfig.setConnectionTimeout(Long.parseLong(config.getProperty("db.pool.connectionTimeout", "30000")));

        // PostgreSQL optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(hikariConfig);
    }

    private void initializeSchema() {
        String createPagesTable = """
                CREATE TABLE IF NOT EXISTS pages (
                    id SERIAL PRIMARY KEY,
                    url TEXT UNIQUE NOT NULL,
                    title TEXT,
                    content TEXT,
                    scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        String createIndexes = """
                CREATE INDEX IF NOT EXISTS idx_pages_url ON pages(url);
                CREATE INDEX IF NOT EXISTS idx_pages_scraped_at ON pages(scraped_at);
                """;

        String createFullTextSearch = """
                ALTER TABLE pages ADD COLUMN IF NOT EXISTS search_vector tsvector;

                CREATE INDEX IF NOT EXISTS idx_search_vector ON pages USING GIN(search_vector);

                CREATE OR REPLACE FUNCTION update_search_vector() RETURNS trigger AS $$
                BEGIN
                    NEW.search_vector :=
                        setweight(to_tsvector('english', COALESCE(NEW.title, '')), 'A') ||
                        setweight(to_tsvector('english', COALESCE(NEW.content, '')), 'B');
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;

                DROP TRIGGER IF EXISTS trigger_update_search_vector ON pages;
                CREATE TRIGGER trigger_update_search_vector
                    BEFORE INSERT OR UPDATE ON pages
                    FOR EACH ROW EXECUTE FUNCTION update_search_vector();
                """;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            stmt.execute(createPagesTable);
            stmt.execute(createIndexes);
            stmt.execute(createFullTextSearch);

            System.out.println("Database schema initialized successfully");
        } catch (SQLException e) {
            System.err.println("Error initializing database schema: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String getProperty(String key) {
        return config.getProperty(key);
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
