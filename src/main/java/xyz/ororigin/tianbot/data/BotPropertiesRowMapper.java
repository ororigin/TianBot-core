package xyz.ororigin.tianbot.data;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import xyz.ororigin.tianbot.utils.Lang;

public final class BotPropertiesRowMapper {

    private static final Class<BotProperties> RECORD_TYPE = BotProperties.class;
    private static final RecordComponent[] COMPONENTS = RECORD_TYPE.getRecordComponents();
    private static final String[] COLUMN_NAMES;
    private static final Constructor<BotProperties> CONSTRUCTOR;

    public static final String KEY_COLUMN;

    public static final String[] VALUE_COLUMN_NAMES;

    static {
        COLUMN_NAMES = new String[COMPONENTS.length];
        for (int i = 0; i < COMPONENTS.length; i++) {
            COLUMN_NAMES[i] = SqliteTypeMapper.toColumnName(COMPONENTS[i].getName());
        }
        KEY_COLUMN = COLUMN_NAMES[0];
        VALUE_COLUMN_NAMES = new String[COMPONENTS.length - 1];
        System.arraycopy(COLUMN_NAMES, 1, VALUE_COLUMN_NAMES, 0, COMPONENTS.length - 1);
        CONSTRUCTOR = findCanonicalConstructor();
    }

    private BotPropertiesRowMapper() {
    }

    private static Constructor<BotProperties> findCanonicalConstructor() {
        for (Constructor<?> candidate : RECORD_TYPE.getDeclaredConstructors()) {
            if (candidate.getParameterCount() == COMPONENTS.length) {
                @SuppressWarnings("unchecked")
                Constructor<BotProperties> canonical = (Constructor<BotProperties>) candidate;
                canonical.setAccessible(true);
                return canonical;
            }
        }
        throw new IllegalStateException(Lang.get("error.bp-no-canonical-ctor"));
    }

    public static String[] allColumnNames() {
        return COLUMN_NAMES.clone();
    }

    public static BotProperties fromRow(ResultSet rs) throws SQLException {
        Object[] args = new Object[COMPONENTS.length];
        for (int i = 0; i < COMPONENTS.length; i++) {
            args[i] = SqliteTypeMapper.readColumn(rs, COLUMN_NAMES[i], COMPONENTS[i].getType());
        }
        try {
            return CONSTRUCTOR.newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new SQLException(Lang.get("error.bp-construct"), e);
        }
    }

    public static void bindAll(PreparedStatement ps, BotProperties props) throws SQLException {
        bindRange(ps, props, 0, COMPONENTS.length, 1);
    }

    public static void bindUpdate(PreparedStatement ps, BotProperties props) throws SQLException {
        // 非主键字段（跳过 index 0 的 uuid），从参数位置 1 开始
        bindRange(ps, props, 1, COMPONENTS.length, 1);
        // 主键值放在最后（WHERE 条件）
        SqliteTypeMapper.bindValue(ps, COMPONENTS.length, COMPONENTS[0].getType(), componentValue(props, 0));
    }

    private static void bindRange(PreparedStatement ps, BotProperties props, int fromComponent, int toComponent, int paramStart) throws SQLException {
        for (int i = fromComponent; i < toComponent; i++) {
            SqliteTypeMapper.bindValue(ps, paramStart + (i - fromComponent), COMPONENTS[i].getType(), componentValue(props, i));
        }
    }

    private static Object componentValue(BotProperties props, int index) {
        try {
            return COMPONENTS[index].getAccessor().invoke(props);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    Lang.t("error.bp-read-field", "field", COMPONENTS[index].getName()), e);
        }
    }
}
