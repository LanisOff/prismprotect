package dev.lanis.prismprotect.fabric.client;

import dev.lanis.prismprotect.config.PrismProtectConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PrismProtectFabricConfigScreen extends Screen {

    private final Screen parent;

    private Button enabledButton;
    private EditBox defaultDurationBox;
    private EditBox maxDurationBox;
    private EditBox pulseTicksBox;
    private EditBox particlesPerBlockBox;

    private boolean highlightEnabled;

    public PrismProtectFabricConfigScreen(Screen parent) {
        super(Component.literal("PrismProtect Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PrismProtectConfig.Data data = PrismProtectConfig.snapshot();
        highlightEnabled = data.highlightEnabled;

        int centerX = width / 2;
        int y = 48;

        enabledButton = addRenderableWidget(
                Button.builder(Component.empty(), button -> {
                            highlightEnabled = !highlightEnabled;
                            syncToggleLabel();
                        })
                        .bounds(centerX - 120, y, 240, 20)
                        .build()
        );
        syncToggleLabel();
        y += 28;

        defaultDurationBox = new EditBox(font, centerX + 20, y, 100, 20, Component.literal(""));
        defaultDurationBox.setValue(Integer.toString(data.defaultHighlightDurationSeconds));
        addRenderableWidget(defaultDurationBox);
        y += 24;

        maxDurationBox = new EditBox(font, centerX + 20, y, 100, 20, Component.literal(""));
        maxDurationBox.setValue(Integer.toString(data.maxHighlightDurationSeconds));
        addRenderableWidget(maxDurationBox);
        y += 24;

        pulseTicksBox = new EditBox(font, centerX + 20, y, 100, 20, Component.literal(""));
        pulseTicksBox.setValue(Integer.toString(data.highlightPulseIntervalTicks));
        addRenderableWidget(pulseTicksBox);
        y += 24;

        particlesPerBlockBox = new EditBox(font, centerX + 20, y, 100, 20, Component.literal(""));
        particlesPerBlockBox.setValue(Integer.toString(data.highlightParticlesPerBlock));
        addRenderableWidget(particlesPerBlockBox);

        addRenderableWidget(
                Button.builder(Component.literal("Save"), button -> saveAndClose())
                        .bounds(centerX - 120, height - 34, 116, 20)
                        .build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("Cancel"), button -> onClose())
                        .bounds(centerX + 4, height - 34, 116, 20)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);

        int centerX = width / 2;
        int y = 78;
        guiGraphics.drawString(font, "Default duration (s)", centerX - 120, y + 6, 0xD0D0D0, false);
        y += 24;
        guiGraphics.drawString(font, "Max duration (s)", centerX - 120, y + 6, 0xD0D0D0, false);
        y += 24;
        guiGraphics.drawString(font, "Pulse interval (ticks)", centerX - 120, y + 6, 0xD0D0D0, false);
        y += 24;
        guiGraphics.drawString(font, "Particles per block", centerX - 120, y + 6, 0xD0D0D0, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void saveAndClose() {
        PrismProtectConfig.Data data = PrismProtectConfig.snapshot();
        data.highlightEnabled = highlightEnabled;
        data.defaultHighlightDurationSeconds = parseInt(defaultDurationBox, data.defaultHighlightDurationSeconds);
        data.maxHighlightDurationSeconds = parseInt(maxDurationBox, data.maxHighlightDurationSeconds);
        data.highlightPulseIntervalTicks = parseInt(pulseTicksBox, data.highlightPulseIntervalTicks);
        data.highlightParticlesPerBlock = parseInt(particlesPerBlockBox, data.highlightParticlesPerBlock);
        PrismProtectConfig.update(data);
        onClose();
    }

    private int parseInt(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void syncToggleLabel() {
        if (enabledButton != null) {
            enabledButton.setMessage(Component.literal("Highlighter: " + (highlightEnabled ? "ON" : "OFF")));
        }
    }
}
