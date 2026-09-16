package xyz.ororigin.tianbot.bot.action.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import xyz.ororigin.tianbot.bot.action.Action;
import xyz.ororigin.tianbot.bot.action.ActionHandler;
import xyz.ororigin.tianbot.bot.action.StopCurrentAction;
import xyz.ororigin.tianbot.bot.action.module.AttackAction;
import xyz.ororigin.tianbot.bot.action.module.ChatAction;
import xyz.ororigin.tianbot.bot.action.module.DismountAction;
import xyz.ororigin.tianbot.bot.action.module.DropAction;
import xyz.ororigin.tianbot.bot.action.module.JumpAction;
import xyz.ororigin.tianbot.bot.action.module.LookAction;
import xyz.ororigin.tianbot.bot.action.module.MountAction;
import xyz.ororigin.tianbot.bot.action.module.MoveAction;
import xyz.ororigin.tianbot.bot.action.module.SneakAction;
import xyz.ororigin.tianbot.bot.action.module.SprintAction;
import xyz.ororigin.tianbot.bot.action.module.SwapHandAction;
import xyz.ororigin.tianbot.bot.action.module.UnSneakAction;
import xyz.ororigin.tianbot.bot.action.module.UnSprintAction;
import xyz.ororigin.tianbot.bot.action.module.UseAction;
import xyz.ororigin.tianbot.bot.action.module.WaitAction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.function.Supplier;


public final class ScriptParser {

    private ScriptParser() {
    }

    public static ScriptDefinition parse(String json, ActionHandler handler) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new ScriptParseException("无效的 script JSON: " + e.getMessage(), e);
        }
        long loops = root.has("loops") ? root.get("loops").getAsLong() : 1L;
        if (loops == 0 || loops < ScriptDefinition.INFINITE) {
            throw new ScriptParseException("loops 必须 >= 1 或 -1(无限循环)，实际: " + loops);
        }
        if (!root.has("steps") || !root.get("steps").isJsonArray()) {
            throw new ScriptParseException("缺少 steps 数组");
        }
        List<Action> steps = new ArrayList<>();
        JsonArray stepsArr = root.getAsJsonArray("steps");
        for (JsonElement el : stepsArr) {
            if (!el.isJsonObject()) {
                throw new ScriptParseException("steps 元素必须是对象: " + el);
            }
            steps.add(parseStep(el.getAsJsonObject(), handler));
        }
        return new ScriptDefinition(loops, steps);
    }

    public static CompletableFuture<Void> parseAndRun(ActionHandler handler, String json) {
        ScriptDefinition def = parse(json, handler);
        return handler.addSequence(def.steps(), def.loops());
    }

    private static Action parseStep(JsonObject step, ActionHandler handler) {
        String name = requireString(step, "action");
        return switch (name) {
            case "attack" -> parseToggleable(step, AttackAction::once, AttackAction::continuous, AttackAction::interval);
            case "use" -> parseToggleable(step, UseAction::once, UseAction::continuous, UseAction::interval);
            case "jump" -> parseToggleable(step, JumpAction::once, JumpAction::continuous, JumpAction::interval);
            case "swapHands" -> parseToggleable(step, SwapHandAction::once, SwapHandAction::continuous, SwapHandAction::interval);
            case "drop" -> parseToggleable(step, DropAction::item, DropAction::itemContinuous, DropAction::itemInterval);
            case "dropStack" -> parseToggleable(step, DropAction::stack, DropAction::stackContinuous, DropAction::stackInterval);
            case "wait" -> WaitAction.of(requireInt(step, "ticks"));
            case "move" -> parseMove(step);
            case "look" -> parseLook(step);
            case "turn" -> LookAction.turn(requireFloat(step, "yaw"), requireFloat(step, "pitch"));
            case "sneak" -> SneakAction.once();
            case "unsneak" -> UnSneakAction.once();
            case "sprint" -> SprintAction.once();
            case "unsprint" -> UnSprintAction.once();
            case "mount" -> (step.has("any") && step.get("any").getAsBoolean())
                    ? MountAction.any()
                    : MountAction.rideable();
            case "dismount" -> DismountAction.once();
            case "say" -> ChatAction.say(requireString(step, "message"));
            case "stopCurrent" -> new StopCurrentAction(handler);
            default -> throw new ScriptParseException("未知 action: " + name);
        };
    }

    private static Action parseToggleable(JsonObject step,
                                          Supplier<Action> once,
                                          Supplier<Action> continuous,
                                          IntFunction<Action> interval) {
        String mode = step.has("mode") ? step.get("mode").getAsString() : "once";
        return switch (mode) {
            case "once" -> once.get();
            case "continuous" -> continuous.get();
            case "interval" -> interval.apply(requireInt(step, "ticks"));
            default -> throw new ScriptParseException("未知 mode: " + mode + "（应为 once/continuous/interval）");
        };
    }

    private static Action parseMove(JsonObject step) {
        if (step.has("dir")) {
            return switch (step.get("dir").getAsString()) {
                case "forward" -> MoveAction.forward();
                case "backward" -> MoveAction.backward();
                case "left" -> MoveAction.left();
                case "right" -> MoveAction.right();
                case "stop" -> MoveAction.stop();
                default -> throw new ScriptParseException("未知 move dir: " + step.get("dir").getAsString());
            };
        }
        float forward = step.has("forward") ? step.get("forward").getAsFloat() : 0F;
        float strafe = step.has("strafe") ? step.get("strafe").getAsFloat() : 0F;
        return MoveAction.vector(forward, strafe);
    }

    private static Action parseLook(JsonObject step) {
        if (step.has("target")) {
            return switch (step.get("target").getAsString()) {
                case "north" -> LookAction.north();
                case "south" -> LookAction.south();
                case "east" -> LookAction.east();
                case "west" -> LookAction.west();
                case "up" -> LookAction.up();
                case "down" -> LookAction.down();
                default -> throw new ScriptParseException("未知 look target: " + step.get("target").getAsString());
            };
        }
        if (step.has("at")) {
            if (!step.get("at").isJsonObject()) {
                throw new ScriptParseException("look at 必须是对象 {x,y,z}");
            }
            JsonObject at = step.getAsJsonObject("at");
            return LookAction.lookAt(new Vec3(
                    requireDouble(at, "x"),
                    requireDouble(at, "y"),
                    requireDouble(at, "z")));
        }
        return LookAction.rotation(requireFloat(step, "yaw"), requireFloat(step, "pitch"));
    }

    private static String requireString(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            throw new ScriptParseException("步骤缺少字符串字段: " + key);
        }
        return obj.get(key).getAsString();
    }

    private static int requireInt(JsonObject obj, String key) {
        if (!obj.has(key)) {
            throw new ScriptParseException("步骤缺少整数字段: " + key);
        }
        try {
            return obj.get(key).getAsInt();
        } catch (RuntimeException e) {
            throw new ScriptParseException("字段 " + key + " 不是有效整数: " + obj.get(key), e);
        }
    }

    private static float requireFloat(JsonObject obj, String key) {
        if (!obj.has(key)) {
            throw new ScriptParseException("步骤缺少数字字段: " + key);
        }
        try {
            return obj.get(key).getAsFloat();
        } catch (RuntimeException e) {
            throw new ScriptParseException("字段 " + key + " 不是有效数字: " + obj.get(key), e);
        }
    }

    private static double requireDouble(JsonObject obj, String key) {
        if (!obj.has(key)) {
            throw new ScriptParseException("对象缺少数字字段: " + key);
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (RuntimeException e) {
            throw new ScriptParseException("字段 " + key + " 不是有效数字: " + obj.get(key), e);
        }
    }
}
