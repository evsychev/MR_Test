package org.example.state;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class UserStateRepository implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserStateRepository.class);
    private static final int SCHEMA_VERSION = 1;
    private final Connection connection;

    private UserStateRepository(Connection connection) {
        this.connection = connection;
    }

    static UserStateRepository open(Path databasePath) throws SQLException {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (java.io.IOException exception) {
            throw new SQLException("Could not create application data directory", exception);
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        UserStateRepository repository = new UserStateRepository(connection);
        try {
            repository.initializeSchema();
        } catch (SQLException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        LOGGER.info("User state database opened at {}", databasePath);
        return repository;
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                if (version.next() && version.getInt(1) > SCHEMA_VERSION) {
                    throw new SQLException("Database schema is newer than this application supports");
                }
            }
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("CREATE TABLE IF NOT EXISTS app_state ("
                    + "user_id TEXT NOT NULL, state_key TEXT NOT NULL, state_value TEXT NOT NULL, "
                    + "PRIMARY KEY (user_id, state_key))");
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }

    public UserAppState load(String userId) throws SQLException {
        requireUserId(userId);
        String sql = "SELECT state_key, state_value FROM app_state WHERE user_id = ?";
        java.util.Map<String, String> values = new java.util.HashMap<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, userId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) values.put(rows.getString(1), rows.getString(2));
            }
        }
        Set<String> expanded = new HashSet<>();
        String expandedValue = values.getOrDefault("expandedNodeIds", "");
        if (!expandedValue.isBlank()) expanded.addAll(java.util.List.of(expandedValue.split("\\n")));
        return new UserAppState(number(values, "widthMeters", UserAppState.DEFAULT_WIDTH_METERS),
                number(values, "heightMeters", UserAppState.DEFAULT_HEIGHT_METERS),
                number(values, "offsetX", 0), number(values, "offsetY", 0), number(values, "offsetZ", 0),
                Boolean.parseBoolean(values.getOrDefault("minimized", "false")),
                values.getOrDefault("selectedNodeId", ""), Set.copyOf(expanded),
                integer(values, "treeScrollOffset", 0), values.getOrDefault("language", ""));
    }

    public void save(String userId, UserAppState state) throws SQLException {
        requireUserId(userId);
        String sql = "INSERT INTO app_state(user_id, state_key, state_value) VALUES(?, ?, ?) "
                + "ON CONFLICT(user_id, state_key) DO UPDATE SET state_value=excluded.state_value";
        connection.setAutoCommit(false);
        try (PreparedStatement upsert = connection.prepareStatement(sql)) {
            put(upsert, userId, "widthMeters", state.widthMeters());
            put(upsert, userId, "heightMeters", state.heightMeters());
            put(upsert, userId, "offsetX", state.offsetX());
            put(upsert, userId, "offsetY", state.offsetY());
            put(upsert, userId, "offsetZ", state.offsetZ());
            put(upsert, userId, "minimized", state.minimized());
            put(upsert, userId, "selectedNodeId", state.selectedNodeId());
            put(upsert, userId, "expandedNodeIds", String.join("\n", state.expandedNodeIds()));
            put(upsert, userId, "treeScrollOffset", state.treeScrollOffset());
            put(upsert, userId, "language", state.language());
            upsert.executeBatch();
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void put(PreparedStatement statement, String userId, String key, Object value)
            throws SQLException {
        statement.setString(1, userId);
        statement.setString(2, key);
        statement.setString(3, String.valueOf(value));
        statement.addBatch();
    }

    private static float number(java.util.Map<String, String> values, String key, float fallback) {
        try {
            float value = Float.parseFloat(values.getOrDefault(key, Float.toString(fallback)));
            return Float.isFinite(value) ? value : fallback;
        }
        catch (NumberFormatException exception) { return fallback; }
    }

    private static int integer(java.util.Map<String, String> values, String key, int fallback) {
        try { return Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback))); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private static void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("A profile user ID is required");
    }

    @Override public void close() throws SQLException { connection.close(); }
}
