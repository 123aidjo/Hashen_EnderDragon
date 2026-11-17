package com.ashenenderdragon.client.screen;

import java.util.List;
import java.util.Locale;

import com.ashenenderdragon.boss.DragonBossState;
import com.ashenenderdragon.client.animation.DragonAnimationDebugSession;
import com.ashenenderdragon.client.animation.DragonAnimationOverrideLibrary;
import com.ashenenderdragon.client.animation.DragonModelPart;
import com.ashenenderdragon.client.animation.DragonPoseTransform;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import org.lwjgl.glfw.GLFW;

/** Transparent in-world editor for additive dragon animation keyframes. */
public final class DragonAnimationDebugScreen extends Screen {
    private static final String[] COMPONENT_NAMES = {"位置 X", "位置 Y", "位置 Z", "旋转 X", "旋转 Y", "旋转 Z"};

    private int actionIndex;
    private int partIndex;
    private Button actionButton;
    private Button partButton;
    private Button playButton;
    private Button idlePoseButton;
    private TimelineSlider timeline;
    private final TransformSlider[] transformSliders = new TransformSlider[6];
    private String status = "";
    private int statusTicks;
    private float lastDisplayedTick = -1.0F;
    private int infoY;
    private int editorWidth;
    private int previewLeft;
    private int previewTop;
    private int previewRight;
    private int previewBottom;
    private float previewYaw = -25.0F;
    private float previewPitch = -8.0F;
    private float previewZoom = 1.0F;
    private boolean rotatingPreview;

    public DragonAnimationDebugScreen() {
        super(Component.literal("末影龙动画调试器"));
        this.actionIndex = indexOf(DragonAnimationDebugSession.ACTIONS, DragonAnimationDebugSession.action());
    }

    @Override
    protected void init() {
        this.editorWidth = Math.min(400, Math.max(300, (int)(this.width * 0.55F)));
        int panelWidth = Math.min(this.editorWidth - 8, this.width - 12);
        int contentWidth = panelWidth - 16;
        int x = 10;
        int y = 26;

        this.previewLeft = Math.min(this.width - 4, this.editorWidth + 4);
        this.previewTop = 8;
        this.previewRight = this.width - 8;
        this.previewBottom = this.height - 8;

        this.addRenderableWidget(Button.builder(Component.literal("保存 JSON"), button -> this.saveJson())
                .bounds(Math.max(130, panelWidth - 88), 6, 82, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.changeAction(-1)).bounds(x, y, 24, 20).build());
        this.actionButton = this.addRenderableWidget(Button.builder(this.actionLabel(), button -> this.changeAction(1)).bounds(x + 28, y, contentWidth - 56, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.changeAction(1)).bounds(x + contentWidth - 24, y, 24, 20).build());

        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.changePart(-1)).bounds(x, y, 24, 20).build());
        this.partButton = this.addRenderableWidget(Button.builder(this.partLabel(), button -> this.changePart(1)).bounds(x + 28, y, contentWidth - 56, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.changePart(1)).bounds(x + contentWidth - 24, y, 24, 20).build());

        y += 24;
        this.playButton = this.addRenderableWidget(Button.builder(this.playLabel(), button -> {
            DragonAnimationDebugSession.setPlaying(!DragonAnimationDebugSession.isPlaying());
            this.playButton.setMessage(this.playLabel());
        }).bounds(x, y, 72, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("-1 帧"), button -> this.step(-1)).bounds(x + 76, y, 62, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+1 帧"), button -> this.step(1)).bounds(x + 142, y, 62, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("回到 0"), button -> {
            DragonAnimationDebugSession.setPlaying(false);
            DragonAnimationDebugSession.setTicks(0.0F);
            this.refreshControls();
        }).bounds(x + 208, y, 68, 20).build());

        y += 28;
        this.timeline = this.addRenderableWidget(new TimelineSlider(x, y, contentWidth, 20));

        y += 30;
        int sliderWidth = (contentWidth - 4) / 2;
        for (int i = 0; i < this.transformSliders.length; i++) {
            int column = i / 3;
            int row = i % 3;
            this.transformSliders[i] = this.addRenderableWidget(new TransformSlider(
                    x + column * (sliderWidth + 4),
                    y + row * 22,
                    sliderWidth,
                    20,
                    i
            ));
        }

        y += 72;
        int third = (contentWidth - 8) / 3;
        this.addRenderableWidget(Button.builder(Component.literal("写入当前关键帧"), button -> this.writeCurrentKeyframe())
                .bounds(x, y, third, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("删除当前关键帧"), button -> this.deleteCurrentKeyframe())
                .bounds(x + third + 4, y, third, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("清空此部位"), button -> this.clearCurrentPart())
                .bounds(x + (third + 4) * 2, y, contentWidth - (third + 4) * 2, 20).build());

        y += 24;
        this.idlePoseButton = this.addRenderableWidget(Button.builder(this.idlePoseLabel(), button -> this.toggleIdlePoseFrame())
                .bounds(x, y, contentWidth, 20).build());

        y += 24;
        int quarter = (contentWidth - 12) / 4;
        this.addRenderableWidget(Button.builder(Component.literal("保存 JSON"), button -> this.saveJson())
                .bounds(x, y, quarter, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("重新载入"), button -> {
            this.setStatus(DragonAnimationOverrideLibrary.load() ? "已重新载入配置" : "载入失败，保留当前数据");
            this.refreshControls();
        }).bounds(x + quarter + 4, y, quarter, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("关闭面板"), button -> this.onClose())
                .bounds(x + (quarter + 4) * 2, y, quarter, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("停止预览"), button -> {
            DragonAnimationDebugSession.stop();
            this.onClose();
        }).bounds(x + (quarter + 4) * 3, y, contentWidth - (quarter + 4) * 3, 20).build());

        if (this.previewRight - this.previewLeft >= 100) {
            this.addRenderableWidget(Button.builder(Component.literal("重置预览视角"), button -> {
                this.previewYaw = -25.0F;
                this.previewPitch = -8.0F;
                this.previewZoom = 1.0F;
            }).bounds(this.previewLeft + 8, this.previewTop + 20, Math.min(110, this.previewRight - this.previewLeft - 16), 18).build());
        }

        this.infoY = y + 26;

        this.refreshControls();
    }

    @Override
    public void tick() {
        if (this.statusTicks > 0) {
            this.statusTicks--;
        }
        float currentTick = DragonAnimationDebugSession.ticks();
        if (Math.abs(currentTick - this.lastDisplayedTick) >= 0.5F) {
            this.refreshControls();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelRight = Math.min(this.editorWidth, this.width - 4);
        graphics.fill(4, 4, panelRight, this.height - 4, 0xD0101118);
        graphics.drawString(this.font, this.title, 10, 10, 0xFFFFFF, false);
        this.renderDragonPreview(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        List<Integer> frames = DragonAnimationOverrideLibrary.keyframeTicks(this.action(), this.part());
        String frameText = frames.isEmpty() ? "无" : frames.toString();
        graphics.drawString(this.font, "关键帧: " + frameText, 10, this.infoY, 0xB8D8FF, false);
        graphics.drawString(this.font, "拖动参数会自动写入当前整数 tick；Esc/F8 关闭面板但保留预览", 10, this.infoY + 12, 0xA0A0A0, false);
        if (this.statusTicks > 0) {
            graphics.drawString(this.font, this.status, 10, this.infoY + 24, 0x80FF9A, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && this.isInsidePreview(mouseX, mouseY)) {
            this.rotatingPreview = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.rotatingPreview && button == 0) {
            this.previewYaw += (float)dragX * 0.8F;
            this.previewPitch = Mth.clamp(this.previewPitch - (float)dragY * 0.6F, -70.0F, 70.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.rotatingPreview) {
            this.rotatingPreview = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isInsidePreview(mouseX, mouseY)) {
            this.previewZoom = Mth.clamp(this.previewZoom * (1.0F + (float)scrollY * 0.1F), 0.35F, 2.5F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_S && hasControlDown()) {
            this.saveJson();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderDragonPreview(GuiGraphics graphics) {
        int previewWidth = this.previewRight - this.previewLeft;
        int previewHeight = this.previewBottom - this.previewTop;
        if (previewWidth < 80 || previewHeight < 80) {
            return;
        }

        graphics.fill(this.previewLeft, this.previewTop, this.previewRight, this.previewBottom, 0xEB555967);
        int horizon = this.previewTop + previewHeight * 2 / 3;
        graphics.fill(this.previewLeft + 1, horizon, this.previewRight - 1, this.previewBottom - 1, 0xEB454957);
        graphics.renderOutline(this.previewLeft, this.previewTop, previewWidth, previewHeight, 0xFFB8C0DA);
        graphics.drawCenteredString(this.font, "3D 动作预览", (this.previewLeft + this.previewRight) / 2, this.previewTop + 7, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "左键拖拽旋转 · 滚轮缩放", (this.previewLeft + this.previewRight) / 2, this.previewBottom - 14, 0xAAB4C8);

        EnderDragon dragon = DragonAnimationDebugSession.previewDragon();
        if (dragon == null) {
            graphics.drawCenteredString(this.font, "预览实体尚未创建", (this.previewLeft + this.previewRight) / 2,
                    (this.previewTop + this.previewBottom) / 2, 0xFF7777);
            return;
        }

        int availableWidth = previewWidth - 20;
        int availableHeight = previewHeight - 54;
        float widthScale = availableWidth / Math.max(1.0F, dragon.getBbWidth() * 1.7F);
        float heightScale = availableHeight / Math.max(1.0F, dragon.getBbHeight() * 1.35F);
        int scale = Math.max(1, Math.round(Math.min(widthScale, heightScale) * this.previewZoom));
        InventoryScreen.renderEntityInInventoryFollowsAngle(
                graphics,
                this.previewLeft + 4,
                this.previewTop + 40,
                this.previewRight - 4,
                this.previewBottom - 20,
                scale,
                0.0F,
                this.previewYaw / 40.0F,
                this.previewPitch / 20.0F,
                dragon
        );
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        return mouseX >= this.previewLeft && mouseX < this.previewRight
                && mouseY >= this.previewTop && mouseY < this.previewBottom;
    }

    private void changeAction(int direction) {
        this.actionIndex = Math.floorMod(this.actionIndex + direction, DragonAnimationDebugSession.ACTIONS.length);
        DragonAnimationDebugSession.setAction(this.action());
        this.actionButton.setMessage(this.actionLabel());
        this.playButton.setMessage(this.playLabel());
        this.refreshControls();
    }

    private void changePart(int direction) {
        DragonModelPart[] parts = DragonModelPart.values();
        this.partIndex = Math.floorMod(this.partIndex + direction, parts.length);
        this.partButton.setMessage(this.partLabel());
        this.refreshControls();
    }

    private void step(int amount) {
        DragonAnimationDebugSession.setPlaying(false);
        DragonAnimationDebugSession.setTicks(DragonAnimationDebugSession.ticks() + amount);
        this.playButton.setMessage(this.playLabel());
        this.refreshControls();
    }

    private void writeCurrentKeyframe() {
        int tick = this.currentIntegerTick();
        DragonAnimationOverrideLibrary.setKeyframe(
                this.action(),
                this.part(),
                tick,
                DragonAnimationOverrideLibrary.sample(this.action(), this.part(), tick)
        );
        this.setStatus("已写入 tick " + tick + " 的关键帧");
    }

    private void deleteCurrentKeyframe() {
        int tick = this.currentIntegerTick();
        this.setStatus(DragonAnimationOverrideLibrary.removeKeyframe(this.action(), this.part(), tick)
                ? "已删除 tick " + tick + " 的关键帧"
                : "当前 tick 没有关键帧");
        this.refreshControls();
    }

    private void clearCurrentPart() {
        DragonAnimationOverrideLibrary.clearPart(this.action(), this.part());
        this.setStatus("已清空当前动作的“" + this.part().displayName() + "”关键帧");
        this.refreshControls();
    }

    private void toggleIdlePoseFrame() {
        DragonAnimationDebugSession.setPlaying(false);
        int tick = this.currentIntegerTick();
        DragonAnimationDebugSession.setTicks(tick);
        if (DragonAnimationOverrideLibrary.isIdlePoseFrame(this.action(), tick)) {
            DragonAnimationOverrideLibrary.unmarkIdlePoseFrame(this.action(), tick);
            this.setStatus("已取消 tick " + tick + " 的静止姿势标记");
        } else {
            DragonAnimationOverrideLibrary.markIdlePoseFrame(this.action(), tick);
            this.setStatus("tick " + tick + " 已恢复为落地静止姿势，可继续编辑");
        }
        this.refreshControls();
    }

    private void saveJson() {
        this.setStatus(DragonAnimationOverrideLibrary.save() ? "已保存 JSON（Ctrl+S）" : "保存失败，请检查日志");
    }

    private void refreshControls() {
        if (this.timeline == null) {
            return;
        }
        this.lastDisplayedTick = DragonAnimationDebugSession.ticks();
        this.timeline.refresh();
        DragonPoseTransform transform = DragonAnimationOverrideLibrary.sample(this.action(), this.part(), this.lastDisplayedTick);
        for (int i = 0; i < this.transformSliders.length; i++) {
            this.transformSliders[i].refresh(transform.component(i));
        }
        this.playButton.setMessage(this.playLabel());
        this.idlePoseButton.active = !DragonBossState.NO_ACTION.equals(this.action());
        this.idlePoseButton.setMessage(this.idlePoseLabel());
    }

    private int currentIntegerTick() {
        return Math.round(DragonAnimationDebugSession.ticks());
    }

    private String action() {
        return DragonAnimationDebugSession.ACTIONS[this.actionIndex];
    }

    private DragonModelPart part() {
        return DragonModelPart.values()[this.partIndex];
    }

    private Component actionLabel() {
        return Component.literal("动作：" + DragonAnimationDebugSession.actionDisplayName(this.action()));
    }

    private Component partLabel() {
        return Component.literal("部位：" + this.part().displayName());
    }

    private Component playLabel() {
        return Component.literal(DragonAnimationDebugSession.isPlaying() ? "暂停" : "播放");
    }

    private Component idlePoseLabel() {
        return Component.literal(DragonAnimationOverrideLibrary.isIdlePoseFrame(this.action(), this.currentIntegerTick())
                ? "取消当前静止帧（保留已写入参数）"
                : "当前帧一键恢复落地静止姿势");
    }

    private void setStatus(String message) {
        this.status = message;
        this.statusTicks = 80;
    }

    private static int indexOf(String[] values, String wanted) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(wanted)) {
                return i;
            }
        }
        return 0;
    }

    private final class TimelineSlider extends AbstractSliderButton {
        TimelineSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.refresh();
        }

        void refresh() {
            this.value = DragonAnimationDebugSession.duration() <= 0
                    ? 0.0
                    : DragonAnimationDebugSession.ticks() / DragonAnimationDebugSession.duration();
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format(
                    Locale.ROOT,
                    "时间轴：%.1f / %d tick（%.2f 秒）",
                    DragonAnimationDebugSession.ticks(),
                    DragonAnimationDebugSession.duration(),
                    DragonAnimationDebugSession.ticks() / 20.0F
            )));
        }

        @Override
        protected void applyValue() {
            DragonAnimationDebugSession.setPlaying(false);
            DragonAnimationDebugSession.setTicks((float)(this.value * DragonAnimationDebugSession.duration()));
            DragonAnimationDebugScreen.this.refreshControls();
        }
    }

    private final class TransformSlider extends AbstractSliderButton {
        private final int component;
        private boolean refreshing;

        TransformSlider(int x, int y, int width, int height, int component) {
            super(x, y, width, height, Component.empty(), 0.5);
            this.component = component;
            this.updateMessage();
        }

        void refresh(float actualValue) {
            this.refreshing = true;
            float min = this.minimum();
            float max = this.maximum();
            this.value = Math.max(0.0, Math.min(1.0, (actualValue - min) / (max - min)));
            this.updateMessage();
            this.refreshing = false;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format(Locale.ROOT, "%s：%.2f%s",
                    COMPONENT_NAMES[this.component], this.actualValue(), this.component >= 3 ? "°" : "")));
        }

        @Override
        protected void applyValue() {
            this.updateMessage();
            if (this.refreshing) {
                return;
            }
            int tick = DragonAnimationDebugScreen.this.currentIntegerTick();
            String action = DragonAnimationDebugScreen.this.action();
            DragonModelPart part = DragonAnimationDebugScreen.this.part();
            DragonPoseTransform current = DragonAnimationOverrideLibrary.sample(action, part, tick);
            DragonAnimationOverrideLibrary.setKeyframe(action, part, tick, current.withComponent(this.component, this.actualValue()));
        }

        private float actualValue() {
            return (float)(this.minimum() + this.value * (this.maximum() - this.minimum()));
        }

        private float minimum() {
            if (this.component >= 3) {
                return -180.0F;
            }
            return DragonAnimationDebugScreen.this.part() == DragonModelPart.ROOT ? -3.0F : -24.0F;
        }

        private float maximum() {
            if (this.component >= 3) {
                return 180.0F;
            }
            return DragonAnimationDebugScreen.this.part() == DragonModelPart.ROOT ? 3.0F : 24.0F;
        }
    }
}
