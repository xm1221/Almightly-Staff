package cn.xm1221.AlmightlyStaff.spell;

/**
 * 一个可绘制的 Hex 图案引用（动作ID + 起始方向 + 角度签名）。
 * 移植自 Hex CyberStaff。
 */
public record PatternRef(String actionId, String startDirection, String signature) {
    private static final String UNKNOWN_ACTION_PREFIX = "almightly_staff:unknown/";

    public PatternRef {
        actionId = actionId == null ? "" : actionId;
        startDirection = startDirection == null || startDirection.isBlank() ? "EAST" : startDirection;
        signature = signature == null ? "" : signature;
    }

    public static PatternRef unknownSymbol(String token) {
        return new PatternRef(UNKNOWN_ACTION_PREFIX + (token == null ? "" : token), "EAST", "");
    }

    public boolean isUnknownSymbol() {
        return actionId.startsWith(UNKNOWN_ACTION_PREFIX);
    }

    public String unknownSymbol() {
        return isUnknownSymbol() ? actionId.substring(UNKNOWN_ACTION_PREFIX.length()) : "";
    }
}
