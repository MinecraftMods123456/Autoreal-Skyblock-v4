package dev.autoreel;

import java.lang.reflect.Field;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/**
 * Auto Reel: client-side auto fishing for Minecraft 26.1.x (Fabric).
 *
 * Press the toggle key (default R, rebindable under Options > Controls > Auto Reel) to start or stop.
 * While on, hold a fishing rod: it casts, waits for the fish to actually bite, reels in, and casts again.
 */
public final class AutoReelClient implements ClientModInitializer {
	public static final String MOD_ID = "autoreel";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** A bobber that drops faster than this (blocks per tick) in one tick is being yanked down by a fish. */
	private static final double DIP_PER_TICK = -0.12;

	private final AutoFishController controller = new AutoFishController();
	private final McGame game = new McGame();

	private KeyMapping toggleKey;
	private boolean enabled;
	private boolean warnedNoRod;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + MOD_ID + ".toggle", GLFW.GLFW_KEY_R, category));
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		LOGGER.info("Auto Reel loaded. Press the toggle key (default R) in a world to start/stop auto fishing.");
	}

	private void onTick(Minecraft mc) {
		// Always drain key presses so they never pile up while a menu is open.
		boolean pressed = false;
		while (toggleKey.consumeClick()) {
			pressed = true;
		}

		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			enabled = false; // left the world: never auto-start on the next join
			return;
		}

		if (pressed) {
			enabled = !enabled;
			if (enabled) {
				controller.reset();
				warnedNoRod = false;
			}
			show(mc, Component.literal("Auto Reel: " + (enabled ? "ON" : "OFF"))
					.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
		}

		// Pause while a menu/chat is open, in the pause screen, or while dead.
		if (!enabled || mc.screen != null || mc.gameMode == null || !player.isAlive()) {
			return;
		}

		game.bind(mc, player);

		if (game.holdingRod()) {
			warnedNoRod = false;
		} else if (!warnedNoRod) {
			warnedNoRod = true;
			show(mc, Component.literal("Auto Reel: hold a fishing rod").withStyle(ChatFormatting.YELLOW));
		}

		controller.tick(game);
	}

	private static void show(Minecraft mc, Component message) {
		mc.gui.setOverlayMessage(message, false);
	}

	/** Connects the fishing logic to the real game. */
	private static final class McGame implements AutoFishController.Game {
		private Minecraft mc;
		private LocalPlayer player;
		private double lastBobberY = Double.NaN;

		void bind(Minecraft mc, LocalPlayer player) {
			this.mc = mc;
			this.player = player;
		}

		@Override
		public boolean holdingRod() {
			return player.getMainHandItem().is(Items.FISHING_ROD);
		}

		@Override
		public boolean bobberOut() {
			Entity bobber = player.fishing;
			boolean out = bobber != null && !bobber.isRemoved();
			if (!out) {
				lastBobberY = Double.NaN;
			}
			return out;
		}

		@Override
		public boolean fishBiting() {
			Entity bobber = player.fishing;
			if (bobber == null) {
				return false;
			}

			// Signal 1: the bobber's own "fish is on the hook" flag (set when the splash happens).
			Boolean flag = BiteFlag.read(bobber);

			// Signal 2: the bobber gets yanked underwater, so its height suddenly drops.
			double y = bobber.getY();
			double previous = lastBobberY;
			lastBobberY = y;
			boolean dipped = !Double.isNaN(previous) && (y - previous) < DIP_PER_TICK;

			return (flag != null && flag) || dipped;
		}

		@Override
		public void useRod() {
			mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			player.swing(InteractionHand.MAIN_HAND);
		}
	}

	/**
	 * Reads the bobber's private "biting" flag. If the game ever renames or removes it, this quietly returns
	 * null and the motion-based detection keeps working on its own.
	 */
	private static final class BiteFlag {
		private static boolean searched;
		private static Field field;

		static Boolean read(Entity bobber) {
			if (!searched) {
				searched = true;
				field = find(bobber.getClass());
				LOGGER.info("Auto Reel bite detection: {}", field != null ? "bobber bite flag + motion" : "bobber motion only");
			}
			if (field == null) {
				return null;
			}
			try {
				return field.getBoolean(bobber);
			} catch (Throwable t) {
				field = null;
				return null;
			}
		}

		private static Field find(Class<?> type) {
			for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
				try {
					Field f = c.getDeclaredField("biting");
					if (f.getType() == boolean.class) {
						f.setAccessible(true);
						return f;
					}
				} catch (Throwable ignored) {
					// not declared here, keep looking up the class chain
				}
			}
			return null;
		}
	}
}
