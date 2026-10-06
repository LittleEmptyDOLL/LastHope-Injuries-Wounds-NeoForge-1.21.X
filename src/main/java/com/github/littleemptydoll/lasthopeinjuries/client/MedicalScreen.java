package com.github.littleemptydoll.lasthopeinjuries.client;

import com.github.littleemptydoll.lasthopeinjuries.network.WoundNetwork;
import com.github.littleemptydoll.lasthopeinjuries.compat.lso.LsoWoundBridge;
import com.github.littleemptydoll.lasthopeinjuries.wound.Dressing;
import com.github.littleemptydoll.lasthopeinjuries.wound.TreatmentItems;
import com.github.littleemptydoll.lasthopeinjuries.wound.Wound;
import com.github.littleemptydoll.lasthopeinjuries.wound.WoundState;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.ModList;

/** Medical view: select a wound on the left and treat it from the right panel. */
public final class MedicalScreen extends Screen {
    private static final int ROWS = 7;
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 260;
    private UUID selectedId;
    private int page;
    private Button bandageButton;
    private Button cleanButton;
    private Button antibioticsButton;
    private Button removeButton;
    private Button medkitButton;
    private Button herbsButton;
    private Button sutureButton;
    private Button previousButton;
    private Button nextButton;

    public MedicalScreen() {
        super(Component.translatable("screen.lasthopeinjuries.medical"));
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"),
                b -> page = Math.max(0, page - 1)).bounds(left + 8, top + 197, 20, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"),
                b -> page++).bounds(left + 155, top + 197, 20, 20).build());
        bandageButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.lasthopeinjuries.bandage"),
                b -> treat(0)).bounds(left + 194, top + 170, 92, 20).build());
        cleanButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.lasthopeinjuries.clean"),
                b -> treat(1)).bounds(left + 290, top + 170, 92, 20).build());
        removeButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.lasthopeinjuries.remove_dressing"),
                b -> removeDressing()).bounds(left + 194, top + 192, 92, 20).build());
        antibioticsButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.lasthopeinjuries.antibiotics"),
                b -> treat(3)).bounds(left + 194, top + 236, 188, 20).build());
        medkitButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.lasthopeinjuries.medkit"),
                b -> treat(4)).bounds(left + 194, top + 214, 92, 20).build());
        herbsButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.lasthopeinjuries.herbs"),
                b -> treat(5)).bounds(left + 290, top + 214, 92, 20).build());
        sutureButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.lasthopeinjuries.suture"),
                b -> treat(6)).bounds(left + 290, top + 192, 92, 20).build());
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

    private boolean hasItem(TagKey<Item> tag) {
        if (minecraft == null || minecraft.player == null) return false;
        return minecraft.player.getOffhandItem().is(tag)
                || minecraft.player.getInventory().items.stream().anyMatch(stack -> stack.is(tag));
    }

    private void treat(int action) {
        Wound wound = selected();
        TagKey<Item> tag = switch (action) {
            case 0 -> TreatmentItems.BANDAGES;
            case 1 -> TreatmentItems.ANTISEPTICS;
            case 3 -> TreatmentItems.ANTIBIOTICS;
            case 4 -> TreatmentItems.MEDKITS;
            case 5 -> TreatmentItems.HERBS;
            case 6 -> TreatmentItems.SUTURES;
            default -> null;
        };
        if (tag == null) return;
        if (wound != null && hasItem(tag)) {
            PacketDistributor.sendToServer(new WoundNetwork.Treat(wound.id().toString(), action));
        }
    }

    private void removeDressing() {
        Wound wound = selected();
        if (wound != null && wound.bandaged()) {
            PacketDistributor.sendToServer(new WoundNetwork.Treat(wound.id().toString(), 2));
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
        bandageButton.setMessage(Component.translatable(wound != null && wound.bandaged()
                ? "screen.lasthopeinjuries.replace_dressing" : "screen.lasthopeinjuries.bandage"));
        bandageButton.active = wound != null && wound.canBandage() && hasItem(TreatmentItems.BANDAGES);
        cleanButton.active = wound != null && !wound.bandaged() && wound.contamination() > 0
                && hasItem(TreatmentItems.ANTISEPTICS);
        antibioticsButton.active = wound != null && wound.infection() > 0
                && hasItem(TreatmentItems.ANTIBIOTICS);
        removeButton.active = wound != null && wound.bandaged();
        medkitButton.active = wound != null && wound.canMedkit() && hasItem(TreatmentItems.MEDKITS);
        herbsButton.active = wound != null && wound.canUseHerbs() && minecraft != null
                && minecraft.player != null && minecraft.player.getFoodData().getFoodLevel() > 3
                && hasItem(TreatmentItems.HERBS);
        sutureButton.active = wound != null && wound.canSuture() && hasItem(TreatmentItems.SUTURES);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        if (button == 0 && mouseX >= left + 8 && mouseX < left + 178
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
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        gui.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xDD151B22);
        gui.fill(left + 185, top + 27, left + 186, top + 256, 0xFF58606A);
        gui.drawString(font, title, left + 9, top + 8, 0xFFFFFF);
        WoundState state = ClientWounds.state();
        gui.drawString(font, Component.translatable("screen.lasthopeinjuries.blood_level",
                Math.round(state.bloodLevel())), left + 278, top + 8, 0xEAA0A0);
        gui.drawString(font, Component.translatable("screen.lasthopeinjuries.bleeding_rate",
                rate(state.bleedingRate())), left + 194, top + 23, 0xEAA0A0);

        int start = page * ROWS;
        for (int row = 0; row < ROWS && start + row < state.wounds().size(); row++) {
            Wound wound = state.wounds().get(start + row);
            int y = top + 35 + row * 22;
            gui.fill(left + 8, y, left + 178, y + 19,
                    wound.id().equals(selectedId) ? 0xFF586B72 : 0xFF303840);
            gui.drawString(font, label("part", wound.part().name()), left + 12, y + 1, 0xFFFFFF);
            gui.drawString(font, label("type", wound.type().name()), left + 12, y + 10, 0xBFC8D0);
        }
        Wound wound = selected();
        if (wound == null) {
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.no_wounds"),
                    left + 194, top + 45, 0xCACACA);
        } else {
            String stage = wound.infection() >= 80 ? "severe" : wound.infection() >= 50 ? "infected"
                    : wound.infection() >= 20 ? "inflamed" : wound.infection() > 0
                    || wound.contamination() >= 40 ? "at_risk" : "clean";
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.stage." + stage),
                    left + 8, top + 230, wound.infection() >= 50 ? 0xFF8888 : 0xDADADA);
            gui.drawString(font, label("type", wound.type().name()), left + 194, top + 36, 0xFFFFFF);
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.severity", wound.effectiveSeverity()),
                    left + 194, top + 48, 0xDADADA);
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.infection", wound.infection()),
                    left + 194, top + 60, 0xDADADA);
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.contamination",
                    wound.contamination()), left + 194, top + 72, 0xDADADA);
            Dressing dressing = wound.dressing();
            Component itemName = dressing == null ? Component.translatable("screen.lasthopeinjuries.no_dressing")
                    : dressingName(dressing.item());
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.dressing", itemName),
                    left + 194, top + 84, 0xDADADA);
            if (ModList.get().isLoaded("legendarysurvivaloverhaul") && minecraft != null
                    && minecraft.player != null) {
                int health = Math.round(100 * LsoWoundBridge.healthRatio(minecraft.player, wound.part()));
                gui.drawString(font, Component.translatable("screen.lasthopeinjuries.lso_health", health),
                        left + 194, top + 96, 0xDADADA);
            }
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.wound_bleeding",
                    rate(wound.bleedingPerSecond())), left + 194, top + 108, 0xEAA0A0);
            gui.drawString(font, Component.translatable("screen.lasthopeinjuries.healing",
                    Math.round(wound.healingProgress())), left + 194, top + 120, 0xAADFAA);
            if (dressing != null) {
                gui.drawString(font, Component.translatable("screen.lasthopeinjuries.cleanliness",
                        Math.round(dressing.cleanliness())), left + 194, top + 132, 0xDADADA);
                gui.drawString(font, Component.translatable("screen.lasthopeinjuries.saturation",
                        Math.round(dressing.saturation())), left + 194, top + 144, 0xDADADA);
            }
        }
        Component status = wound == null ? Component.translatable("screen.lasthopeinjuries.hold_item")
                : wound.contamination() >= 40 ? Component.translatable(wound.bandaged()
                ? "screen.lasthopeinjuries.remove_first" : "screen.lasthopeinjuries.clean_first")
                : wound.herbSeconds() > 0 ? Component.translatable("screen.lasthopeinjuries.herbs_active",
                (wound.herbSeconds() + 59) / 60)
                : Component.translatable(wound.sutured() && wound.sutureIntegrity() < 70
                ? "screen.lasthopeinjuries.damaged_suture" : wound.sutured() ? "screen.lasthopeinjuries.sutured"
                : wound.stabilized() ? "screen.lasthopeinjuries.stabilized"
                : "screen.lasthopeinjuries.active_bleeding");
        gui.drawString(font, status,
                left + 194, top + 157, 0xAAB2BD);
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

    private static Component dressingName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return Component.literal(itemId);
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? Component.literal(id.getPath()) : item.getDescription();
    }

    private static Component label(String kind, String name) {
        return Component.translatable(kind + ".lasthopeinjuries." + name.toLowerCase(Locale.ROOT));
    }
}
