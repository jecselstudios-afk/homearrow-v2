package com.example.homearrow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

public class HomeArrowClient implements ClientModInitializer {
    private static boolean arrowVisible = false;

    @Override
    public void onInitializeClient() {
        HomeStore.load();

        KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.homearrow.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "key.categories.homearrow"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                arrowVisible = !arrowVisible;
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> renderArrow(drawContext));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("sethome").executes(ctx -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player == null || client.world == null) return 0;

                HomeStore.set(new HomeStore.Home(
                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ(),
                        dimensionId(client)));

                client.player.sendMessage(Text.literal("Home set here. Press N to toggle the arrow."), false);
                return 1;
            }));

            dispatcher.register(ClientCommandManager.literal("clearhome").executes(ctx -> {
                MinecraftClient client = MinecraftClient.getInstance();
                HomeStore.clear();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Home cleared."), false);
                }
                return 1;
            }));
        });
    }

    private static void renderArrow(DrawContext ctx) {
        if (!arrowVisible) return;

        MinecraftClient client = MinecraftClient.getInstance();
        HomeStore.Home home = HomeStore.get();
        if (home == null || client.player == null || client.world == null || client.options.hudHidden) return;

        int centerX = ctx.getScaledWindowWidth() / 2;
        int centerY = ctx.getScaledWindowHeight() / 3;

        if (!home.dimension().equals(dimensionId(client))) {
            ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal("Home is in another dimension"), centerX, centerY, 0xFFFFFF);
            return;
        }

        double dx = home.x() - client.player.getX();
        double dz = home.z() - client.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 3) {
            ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal("You are home"), centerX, centerY, 0x55FF55);
            return;
        }

        // Work out where home is relative to the direction the player is facing.
        double yaw = Math.toRadians(client.player.getYaw());
        double forward = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
        double right = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
        float angle = (float) Math.atan2(right, forward);

        drawArrow(ctx, centerX, centerY, angle);

        ctx.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal(Math.round(dist) + " blocks"), centerX, centerY + 18, 0xFFFFFF);
    }

    /** Draws an arrow pointing up, then rotates it by the given angle (radians, clockwise on screen). */
    private static void drawArrow(DrawContext ctx, int x, int y, float angle) {
        MatrixStack matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.multiply(new Quaternionf().rotationZ(angle));

        int color = 0xFFFFD700; // gold
        ctx.fill(-2, -10, 2, 12, color); // shaft
        for (int i = 0; i < 12; i++) {   // arrowhead, widening toward the base
            int half = i + 1;
            ctx.fill(-half, -22 + i, half, -21 + i, color);
        }

        matrices.pop();
    }

    private static String dimensionId(MinecraftClient client) {
        return client.world.getRegistryKey().getValue().toString();
    }
}
