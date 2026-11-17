package com.ashenenderdragon.client.animation;

/** Parts exposed by the in-game dragon animation editor. */
public enum DragonModelPart {
    ROOT("整体"),
    BODY("身体"),
    HEAD("头部"),
    JAW("下颚"),
    NECK("颈部（全部）"),
    TAIL("尾部（全部）"),
    LEFT_WING("左翼"),
    LEFT_WING_TIP("左翼尖"),
    RIGHT_WING("右翼"),
    RIGHT_WING_TIP("右翼尖"),
    LEFT_FRONT_LEG("左前腿"),
    LEFT_FRONT_LEG_TIP("左前小腿"),
    LEFT_FRONT_FOOT("左前爪"),
    RIGHT_FRONT_LEG("右前腿"),
    RIGHT_FRONT_LEG_TIP("右前小腿"),
    RIGHT_FRONT_FOOT("右前爪"),
    LEFT_REAR_LEG("左后腿"),
    LEFT_REAR_LEG_TIP("左后小腿"),
    LEFT_REAR_FOOT("左后爪"),
    RIGHT_REAR_LEG("右后腿"),
    RIGHT_REAR_LEG_TIP("右后小腿"),
    RIGHT_REAR_FOOT("右后爪");

    private final String displayName;

    DragonModelPart(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
