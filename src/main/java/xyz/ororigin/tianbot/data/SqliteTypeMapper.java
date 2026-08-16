package xyz.ororigin.tianbot.data;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;
import java.util.UUID;
import xyz.ororigin.tianbot.utils.Lang;

public final class SqliteTypeMapper {

    private SqliteTypeMapper() {
    }


    public static String toColumnName(String fieldName) {
        return fieldName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }


    public static String toSqlType(Class<?> type) {
        if (type == String.class || type == UUID.class) {
            return "TEXT";
        }
        if (type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == boolean.class || type == Boolean.class) {
            return "INTEGER";
        }
        if (type == float.class || type == Float.class
                || type == double.class || type == Double.class) {
            return "REAL";
        }
        throw new IllegalArgumentException(
                Lang.t("error.db-unsupported-field-type", "type", type.getName()));
    }

    /**
     * 该类型是否为受支持的可持久化标量类型（String/UUID/各数字类型/boolean 及包装类）。
     */
    public static boolean isSupportedType(Class<?> type) {
        return type == String.class || type == UUID.class
                || type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == boolean.class || type == Boolean.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class;
    }

    /**
     * 原始类型 → 对应包装类（非原始类型原样返回）。用于类型匹配校验。
     */
    public static Class<?> boxedType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        return type;
    }


    public static Object readColumn(ResultSet rs, String columnName, Class<?> targetType) throws SQLException {
        if (targetType == String.class) {
            String value = rs.getString(columnName);
            return value != null ? value : "";
        }
        if (targetType == UUID.class) {
            String value = rs.getString(columnName);
            return value != null ? UUID.fromString(value) : null;
        }
        if (targetType == byte.class || targetType == Byte.class) {
            byte value = rs.getByte(columnName);
            return rs.wasNull() ? (byte) 0 : value;
        }
        if (targetType == short.class || targetType == Short.class) {
            short value = rs.getShort(columnName);
            return rs.wasNull() ? (short) 0 : value;
        }
        if (targetType == int.class || targetType == Integer.class) {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? 0 : value;
        }
        if (targetType == long.class || targetType == Long.class) {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? 0L : value;
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            boolean value = rs.getBoolean(columnName);
            return rs.wasNull() ? false : value;
        }
        if (targetType == float.class || targetType == Float.class) {
            float value = rs.getFloat(columnName);
            return rs.wasNull() ? 0f : value;
        }
        if (targetType == double.class || targetType == Double.class) {
            double value = rs.getDouble(columnName);
            return rs.wasNull() ? 0d : value;
        }
        throw new IllegalArgumentException(
                Lang.t("error.db-unsupported-field-type", "type", targetType.getName()));
    }


    public static void bindValue(PreparedStatement ps, int index, Class<?> fieldType, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NULL);
            return;
        }
        if (fieldType == UUID.class) {
            ps.setString(index, value.toString());
            return;
        }
        if (fieldType == boolean.class || fieldType == Boolean.class) {
            ps.setInt(index, ((Boolean) value) ? 1 : 0);
            return;
        }
        ps.setObject(index, value);
    }
}
