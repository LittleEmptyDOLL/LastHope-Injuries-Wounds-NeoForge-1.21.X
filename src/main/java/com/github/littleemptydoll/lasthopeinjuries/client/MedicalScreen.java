package com.github.littleemptydoll.lasthopeinjuries.client;

import com.github.littleemptydoll.lasthopeinjuries.network.WoundNetwork;
import com.github.littleemptydoll.lasthopeinjuries.compat.lso.LsoWoundBridge;
import com.github.littleemptydoll.lasthopeinjuries.wound.TreatmentItems;
import com.github.littleemptydoll.lasthopeinjuries.wound.Wound;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundState;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.ModList;

/** Initial medical view: one selected wound and a server-validated action using a held item. */
public final class MedicalScreen extends Screen {
    private static final int ROWS = 5;
    private UUID selectedId;
    private int page;
    private Button bandageButton;
    private Button cleanButton;
    private Button previousButton;
    private Button nextButton;

    public MedicalScreen() {
        super(Component.translatable("screen.lasthopeinjuries.medical"));
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - 320) / 2;
        int top = (height - 202) / 2;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"),
                b -> page = Math.max(0, page - 1)).bounds(left + 8, top + 151, 20, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"),
                b -> page++).bounds(left + 135, top + 151, 20, 20).build());
        bandageButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.lasthopeinjuries.bandage"),
                b -> treat(true)).bounds(left + 169, top + 150, 68, 20).build());
        cleanButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.lasthopeinjuries.clean"),
                b -> treat(false)).bounds(left + 240, top + 150, 68, 20).build());
    }

    private Wound selected() {
        WoundState state = ClientWounds.state();
        Wound found = state.wounds().stream().filter(w -> w.id().equals(selectedId)).findFirst().orElse(null);
        if (found != null) return found;
        if (state.wounds().isEmpty()) {
            selectedId = null;
            return null;
        }
        found = state.wounds().get(0);
        selectedId = found.id();
        return found;
    }

    private InteractionHand handFor(TagKey<Item> tag) {
        if (minecraft == null || minecraft.player == null) return null;
        if (minecraft.player.getMainHandItem().is(tag)) return InteractionHand.MAIN_HAND;
        if (minecraft.player.getOffhandItem().is(tag)) return InteractionHand.OFF_HAND;
        return null;
    }

    private void treat(boolean bandage) {
        Wound wound = selected();
        InteractionHand hand = handFor(bandage ? TreatmentItems.BANDAGES : TreatmentItems.ANTISEPTICS);
        if (wound != null && hand != null) {
            PacketDistributor.sendToServer(new WoundNetwork.Treat(
                    wound.id().toString(), bandage ? 0 : 1, hand == InteractionHand.MAIN_HAND ? 0 : 1));
        }
    }

    @Override
    public void tick() {
        super.tick();
        Wound wound = selected();
        int count = ClientWounds.state().wounds().size();
        page = Math.min(page, Math.max(0, (count - 1) / ROWS));
        previousButton.active = page > 0;
        nextButton.active = (page + 1) * ROWS < count;
        bandageButton.active = wound != null && !wound.bandaged()
                && handFor(TreatmentItems.BANDAGES) != null;
        cleanButton.active = wound != null && (wound.contamination() > 0 || wound.infection() > 0)
                && handFor(TreatmentItems.ANTISEPTICS) != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int left = (width - 320) / 2;
        int top = (height - 202) / 2;
        if (button == 0 && mouseX >= left + 8 && mouseX < left + 157
                && mouseY >= top + 35 && mouseY < top + 35 + ROWS * 22) {
            int index = page * ROWS + ((int) mouseY - top - 35) / 22;
            var wounds = ClientWounds.state().wounds();
            if (index < wounds.size()) {
                selectedId = wounds.get(index).id();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int left = (width - 320) / 2;
        int top = (height - 202) / 2;
        gui.fill(left, top, left + 320, top + 202, 0xDD151B22);
        gui.fill(left + 163, top + 27, left + 164, top + 177, 0xFF58606A);
        gui.drawString(font, title, left + 9, top + 8, 0xFFFFFF);
        WoundState state = ClientWounds.state();
        gui.drawString(font, Component.translatable("screen.lasthopeinjuries.blood_level",
                Math.round(state.bloodLevel())), left + 190, top + 8, 0xEAA0A0);
        gui.drawString(font, Component.translatable("screen.lasthopeinjuries.bleeding_rate",
                rate(state.bleedingRate())), left + 174, top + 23, 0xEAA0A0);

        int start = page * ROWS;
        for (int row = 0; row < ROWS && start + row < state.wounds().size(); row++) {
            Wound wound = state.wounds().get(start + row);
            int y = top + 35 + row * 22;
            gui.fill(left + 8, y, left + 157, y + 19,
                    wound.id().equals(selectedId) ? 0xFF586B72 : 0xFF303840);
            gui.drawString(font, label("part", wound.part().name()), left + 12, y + 1, 0xFFFFFF);
            gui.drawString(font, label("type", wound.type().name()), left + 12, y + 10, 0xBFC8D0);
        }
        Wound wound = selected();
        if (wound == null) {
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.no_wounds"),
                    left + 180, top + 45, 0xCACACA);
        } else {
            gui.drawString(font, label("type", wound.type().name()), left + 174, top + 39, 0xFFFFFF);
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.severity", wound.severity()),
                    left + 174, top + 58, 0xDADADA);
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.infection", wound.infection()),
                    left + 174, top + 74, 0xDADADA);
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.contamination",
                    wound.contamination()), left + 174, top + 90, 0xDADADA);
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.bandaged",
                    wound.bandaged() ? Component.translatable("gui.yes") : Component.translatable("gui.no")),
                    left + 174, top + 106, 0xDADADA);
            if (ModList.get().isLoaded("legendarysurvivaloverhaul") && minecraft != null
                    && minecraft.player != null) {
                int health = Math.round(100 * LsoWoundBridge.healthRatio(minecraft.player, wound.part()));
                gui.drawString(font, Component.translatable("screen.lasthopeinjuries.lso_health", health),
                        left + 174, top + 122, 0xDADADA);
            }
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.wound_bleeding",
                    rate(wound.bleedingPerSecond())), left + 174, top + 138, 0xEAA0A0);
        }
        gui.drawString(font, Component.translatable("screen.lasthopeinjuries.hold_item"),
                left + 9, top + 184, 0xAAB2BD);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // Screen.render invokes this after our panel and text, before its widgets.
        // The vanilla implementation would blur what we have already drawn.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (LastHopeInjuriesClient.isMedicalKey(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String rate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Component label(String kind, String name) {
        return Component.translatable(kind + ".lasthopeinjuries." + name.toLowerCase(Locale.ROOT));
    }
}
