package xyz.ororigin.tianbot.data;

import xyz.ororigin.tianbot.utils.Lang;

import java.util.UUID;


public final class CustomPropertyCodec {

    private CustomPropertyCodec() {
    }

    public static String serialize(Class<?> type, Object value) {
        if (type == UUID.class) {
            return value.toString();
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.toString((Boolean) value);
        }
        if (type == String.class) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    public static Object deserialize(Class<?> type, String text) {
        if (text == null) {
            return defaultValue(type);
        }
        if (type == String.class) {
            return text;
        }
        if (type == UUID.class) {
            return UUID.fromString(text);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(text);
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.parseByte(text);
        }
        if (type == short.class || type == Short.class) {
            return Short.parseShort(text);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(text);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(text);
        }
        if (type == float.class || type == Float.class) {
            return Float.parseFloat(text);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(text);
        }
        throw new IllegalArgumentException(
                Lang.t("error.bp-type-unsupported", "type", type.getName()));
    }

    public static void validateValue(Class<?> type, Object value) {
        if (value == null) {
            return;
        }
        if (!SqliteTypeMapper.boxedType(type).isInstance(value)) {
            throw new IllegalArgumentException(Lang.t("error.bp-type-mismatch",
                    "expected", type.getName(),
                    "actual", value.getClass().getName()));
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }
}
