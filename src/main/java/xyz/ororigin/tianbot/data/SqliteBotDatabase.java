package xyz.ororigin.tianbot.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;


public class SqliteBotDatabase implements IBotDatabase {

    private static final String QUOTE = "`";

    private final String jdbcUrl;
    private final Executor executor;

    private final String insertSql;
    private final String upsertSql;
    private final String updateSql;
    private final String selectSql;
    private final String deleteSql;

    private final String upsertCustomSql;
    private final String selectCustomSql;
    private final String deleteCustomSql;
    private final String deleteAllCustomSql;

    public SqliteBotDatabase(java.nio.file.Path dbPath, Executor executor) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        this.executor = executor;

        String[] columns = BotPropertiesRowMapper.allColumnNames();
        int columnCount = columns.length;
        String columnList = Arrays.stream(columns)
                .map(c -> QUOTE + c + QUOTE)
                .collect(Collectors.joining(", "));
        String placeholders = String.join(", ", Collections.nCopies(columnCount, "?"));

        // 非主键列（主键 = 第一个 column），用于 UPDATE 的 SET / UPSERT 的 excluded
        String[] valueColumns = Arrays.copyOfRange(columns, 1, columnCount);
        String setClause = Arrays.stream(valueColumns)
                .map(c -> QUOTE + c + QUOTE + " = ?")
                .collect(Collectors.joining(", "));
        String upsertClause = Arrays.stream(valueColumns)
                .map(c -> QUOTE + c + QUOTE + " = excluded." + QUOTE + c + QUOTE)
                .collect(Collectors.joining(", "));

        String keyColumn = QUOTE + BotPropertiesRowMapper.KEY_COLUMN + QUOTE;
        String table = QUOTE + SqliteSchemaMigrator.TABLE_NAME + QUOTE;

        this.insertSql = "INSERT OR IGNORE INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";
        this.upsertSql = "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")"
                + " ON CONFLICT(" + keyColumn + ") DO UPDATE SET " + upsertClause;
        this.updateSql = "UPDATE " + table + " SET " + setClause + " WHERE " + keyColumn + " = ?";
        this.selectSql = "SELECT * FROM " + table + " WHERE " + keyColumn + " = ?";
        this.deleteSql = "DELETE FROM " + table + " WHERE " + keyColumn + " = ?";

        String customTable = QUOTE + SqliteSchemaMigrator.CUSTOM_TABLE_NAME + QUOTE;
        this.upsertCustomSql = "INSERT INTO " + customTable
                + " (`uuid`, `prop_key`, `prop_value`) VALUES (?, ?, ?)"
                + " ON CONFLICT(`uuid`, `prop_key`) DO UPDATE SET `prop_value` = excluded.`prop_value`";
        this.selectCustomSql = "SELECT `prop_key`, `prop_value` FROM " + customTable + " WHERE `uuid` = ?";
        this.deleteCustomSql = "DELETE FROM " + customTable + " WHERE `uuid` = ? AND `prop_key` = ?";
        this.deleteAllCustomSql = "DELETE FROM " + customTable + " WHERE `uuid` = ?";
    }

    @Override
    public CompletableFuture<Void> create(BotProperties botProperties) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(this.insertSql)) {
                BotPropertiesRowMapper.bindAll(ps, botProperties);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> delete(UUID uuid) {
        return submit(conn -> {
            conn.setAutoCommit(false);
            try {
                // 级联删除自定义属性行
                try (PreparedStatement ps = conn.prepareStatement(this.deleteAllCustomSql)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(this.deleteSql)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<BotProperties>> get(UUID uuid) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(this.selectSql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(BotPropertiesRowMapper.fromRow(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> modify(BotProperties botProperties) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(this.upsertSql)) {
                BotPropertiesRowMapper.bindAll(ps, botProperties);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 供兼容场景使用：显式 UPDATE（区别于 UPSERT 语义）。
     * 行不存在时不会创建。
     */
    public CompletableFuture<Void> updateOnly(BotProperties botProperties) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(this.updateSql)) {
                BotPropertiesRowMapper.bindUpdate(ps, botProperties);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> setCustomProperty(UUID uuid, String key, String value) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(this.upsertCustomSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, key);
                ps.setString(3, value);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> setCustomProperties(UUID uuid, Map<String, String> values) {
        if (values.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return submit(conn -> {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(this.upsertCustomSql)) {
                for (Map.Entry<String, String> e : values.entrySet()) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, e.getKey());
                    ps.setString(3, e.getValue());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> getCustomProperties(UUID uuid) {
        return submit(conn -> {
            Map<String, String> result = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(this.selectCustomSql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.put(rs.getString("prop_key"), rs.getString("prop_value"));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<Void> deleteCustomProperty(UUID uuid, String key) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(this.deleteCustomSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, key);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deleteCustomProperties(UUID uuid) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(this.deleteAllCustomSql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private <T> CompletableFuture<T> submit(SqlSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            this.executor.execute(() -> {
                try (Connection conn = openConnection()) {
                    future.complete(supplier.apply(conn));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (Throwable t) {
            // executor 已关闭等情况
            future.completeExceptionally(t);
        }
        return future;
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(this.jdbcUrl);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T apply(Connection conn) throws SQLException;
    }
}
