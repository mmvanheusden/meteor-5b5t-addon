package com.lttstore.duper.modules;

import com.lttstore.duper.DuperAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;

import java.util.List;

import static net.minecraft.item.Items.CRAFTING_TABLE;
import static net.minecraft.item.Items.STICK;

public class Auto5b5tDupe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Recipe> recipeMode = sgGeneral.add(new EnumSetting.Builder<Recipe>().name("recipe").description("Recipe to craft. Make sure you have the ingredients TO CRAFT ATLEAST 2.").defaultValue(Recipe.Stick).build());
    private final Setting<Boolean> single = sgGeneral.add(new BoolSetting.Builder().name("single").description("Just the exploit, not the automation.").defaultValue(false).build());
    private final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>().name("rotation-mode").description("Rotation mode.").defaultValue(RotationMode.Silent).visible(() -> !single.get()).build());

    private Phase phase = Phase.PREPARE;
    private RecipeFinder recipeFinder;
    private RecipeDisplayEntry stickRecipe;
    private float oldPitch;


    public Auto5b5tDupe() {
        super(DuperAddon.CATEGORY, "auto-5b5t-dupe", "Automatically dupes on 5b5t.");
    }


    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        switch (phase) {
            case PREPARE -> {
                mc.player.getInventory().populateRecipeFinder(recipeFinder);
                if (!placeRecipe(recipeFinder)) {
                    toggle();
                    return;
                }
//                placeRecipe(recipeFinder);

                if (rotationMode.get() == RotationMode.Client) {
                    oldPitch = mc.player.getPitch();
                }
                rotate();
                phase = Phase.DROP;
            }
            case DROP -> {
                mc.player.dropSelectedItem(false);
                ChatUtils.info("Dropped item in hand");

                phase = Phase.CRAFT;
            }
            case CRAFT -> {
                if (rotationMode.get() == RotationMode.Client) {
                    mc.player.setPitch(oldPitch);
                }
                mc.player.networkHandler.sendPacket(new CraftRequestC2SPacket(mc.player.currentScreenHandler.syncId, stickRecipe.id(), false)); //TODO: after pickup, move ingredients back to inventory from crafting grid
                toggle();
            }
        }
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }
        recipeFinder = new RecipeFinder();

        if (single.get()) {
            mc.player.getInventory().populateRecipeFinder(recipeFinder);
            if (!placeRecipe(recipeFinder)) {
                toggle();
                return;
            }
            mc.player.networkHandler.sendPacket(new CraftRequestC2SPacket(mc.player.currentScreenHandler.syncId, stickRecipe.id(), false));
            toggle();
            return;
        }

        if (mc.player.getInventory().getSelectedStack().isEmpty()) {
            ChatUtils.error("Hold an item to dupe in your hand.");
            toggle();
            return;
        }

        phase = Phase.PREPARE;
    }

    private void rotate() {
        switch (rotationMode.get()) {
            case RotationMode.Silent -> {
                mc.world.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.headYaw, 90f, true, false));
            }
            case RotationMode.Client -> {
                mc.player.setPitch(90);

            }
        }
    }

    boolean placeRecipe(RecipeFinder recipeFinder) {
        List<RecipeResultCollection> recipeList = mc.player.getRecipeBook().getOrderedResults();
        for (RecipeResultCollection recipe : recipeList) {
            for (RecipeDisplayEntry entry : recipe.getAllRecipes()) {
                RecipeDisplay display = entry.display();
                List<ItemStack> resultStacks = display.result().getStacks(SlotDisplayContexts.createParameters(mc.world));
                for (ItemStack resultStack : resultStacks) {
                    if (resultStack.getItem() == recipeMode.get().item) {
                        // Check if we have ingredients
                        if (!entry.isCraftable(recipeFinder)) {
                            ChatUtils.error("No ingredients in inventory for " + recipeMode.get().item.getName().getString());
//                            toggle();
                            return false;
                        }

                        stickRecipe = entry;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private enum Phase {
        PREPARE, DROP, CRAFT
    }

    private enum RotationMode {
        Silent, Client
    }


    private enum Recipe {
        Stick(STICK), CraftingTable(CRAFTING_TABLE);

        final Item item;

        Recipe(Item item) {
            this.item = item;
        }
    }
}
