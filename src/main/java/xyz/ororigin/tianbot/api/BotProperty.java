package xyz.ororigin.tianbot.api;

import org.jetbrains.annotations.Nullable;
import xyz.ororigin.tianbot.data.SqliteTypeMapper;
import xyz.ororigin.tianbot.utils.Lang;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 一个可被外部插件注册的假人自定义属性描述。
 *
 * <p>{@link #key()} 即数据库 EAV 表中该属性的 prop_key；{@link #type()} 必须是
 * {@code SqliteTypeMapper} 支持的基础标量类型（String / UUID / byte / short / int / long /
 * boolean / float / double 及对应包装类）。{@link #defaultValue()} 用于该假人尚未写入任何
 * 值时读取的兜底；可以为 {@code null} 表示无默认值（读取时返回空）。</p>
 *
 * <p>构造时即完成校验（键非空、键格式 {@code [a-z0-9_]+}、类型受支持、默认值类型匹配），
 * 不合法直接抛 {@link IllegalArgumentException}。键会被规范化为小写。</p>
 *
 * @param key          属性键（存库时作为 prop_key），小写字母/数字/下划线
 * @param type         属性值的类型（必须受 {@code SqliteTypeMapper} 支持）
 * @param defaultValue 缺省值，可为 {@code null}
 */
public record BotProperty(
        String key,
        Class<?> type,
        @Nullable Object defaultValue
) {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_]+");

    public BotProperty {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(Lang.get("error.bp-key-blank"));
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    Lang.t("error.bp-key-invalid", "key", key));
        }
        if (type == null || !SqliteTypeMapper.isSupportedType(type)) {
            throw new IllegalArgumentException(
                    Lang.t("error.bp-type-unsupported",
                            "type", type != null ? type.getName() : "null"));
        }
        if (defaultValue != null && !SqliteTypeMapper.boxedType(type).isInstance(defaultValue)) {
            throw new IllegalArgumentException(Lang.t("error.bp-default-mismatch",
                    "key", normalized,
                    "expected", type.getName(),
                    "actual", defaultValue.getClass().getName()));
        }
        key = normalized;
    }

    /** 以指定键与类型创建属性（无缺省值）。 */
    public static BotProperty of(String key, Class<?> type) {
        return new BotProperty(key, type, null);
    }

    /** 以指定键、类型与缺省值创建属性。 */
    public static BotProperty of(String key, Class<?> type, @Nullable Object defaultValue) {
        return new BotProperty(key, type, defaultValue);
    }
}
