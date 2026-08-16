package xyz.ororigin.tianbot.data;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import xyz.ororigin.tianbot.utils.Lang;

public final class SqliteSchemaMigrator {

    public static final String TABLE_NAME = "bot_properties";

    /** 自定义属性 EAV 表（外部插件经 API 注册的属性值）。 */
    public static final String CUSTOM_TABLE_NAME = "bot_custom_properties";

    private SqliteSchemaMigrator() {
    }


    public static void migrate(Path dbPath, Logger logger) throws SQLException {
        Path parent = dbPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (java.io.IOException e) {
                throw new SQLException(Lang.t("error.db-create-dir", "path", parent), e);
            }
        }
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            initPragmas(conn);
            ensureTable(conn);
            ensureCustomTable(conn);
            addMissingColumns(conn, logger);
        }
    }

    private static void initPragmas(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=3000");
            st.execute("PRAGMA foreign_keys=ON");
        }
    }

    private static void ensureTable(Connection conn) throws SQLException {
        RecordComponent[] components = BotProperties.class.getRecordComponents();
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS `").append(TABLE_NAME).append("` (");
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                ddl.append(", ");
            }
            String column = SqliteTypeMapper.toColumnName(components[i].getName());
            ddl.append('`').append(column).append("` ").append(SqliteTypeMapper.toSqlType(components[i].getType()));
        }
        String keyColumn = SqliteTypeMapper.toColumnName(components[0].getName());
        ddl.append(", PRIMARY KEY (`").append(keyColumn).append("`))");
        try (Statement st = conn.createStatement()) {
            st.execute(ddl.toString());
        }
    }

    /**
     * 建自定义属性 EAV 表：{@code uuid + prop_key} 复合主键，一 bot 一属性一行。
     */
    private static void ensureCustomTable(Connection conn) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS `" + CUSTOM_TABLE_NAME + "` ("
                + "`uuid` TEXT NOT NULL, "
                + "`prop_key` TEXT NOT NULL, "
                + "`prop_value` TEXT, "
                + "PRIMARY KEY (`uuid`, `prop_key`))";
        try (Statement st = conn.createStatement()) {
            st.execute(ddl);
            st.execute("CREATE INDEX IF NOT EXISTS `idx_bot_custom_uuid` ON `"
                    + CUSTOM_TABLE_NAME + "` (`uuid`)");
        }
    }

    private static void addMissingColumns(Connection conn, Logger logger) throws SQLException {
        Set<String> existing = existingColumns(conn);
        List<String> added = new ArrayList<>();
        for (RecordComponent component : BotProperties.class.getRecordComponents()) {
            String column = SqliteTypeMapper.toColumnName(component.getName());
            if (!existing.contains(column)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE `" + TABLE_NAME + "` ADD COLUMN `" + column + "` "
                            + SqliteTypeMapper.toSqlType(component.getType()));
                }
                added.add(column);
            }
        }
    }

    private static Set<String> existingColumns(Connection conn) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(`" + TABLE_NAME + "`)")) {
            while (rs.next()) {
                columns.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }
}
