package com.example.legit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class LegitClient implements ClientModInitializer {
	public static final Module TRIGGER = new Module("Triggerbot");
	public static final Module CRITS = new Module("Trigger Crits");
	public static final Module INVIS = new Module("Invisible Hitbox");
	public static final Module STORAGE = new Module("Storage ESP");
	public static final List<Module> MODULES = List.of(TRIGGER, CRITS, INVIS, STORAGE);

	private static final Random RNG = new Random();
	private static boolean guiWasDown = false;
	private static int delay = 0;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(LegitClient::tick);
		Esp.register();
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		// тик идёт в главном (render) потоке, поэтому текущий GLFW-контекст = окно игры
		long w = GLFW.glfwGetCurrentContext();
		if (w == 0L) return;

		// Click GUI на Right Shift
		boolean gui = GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		if (gui && !guiWasDown && mc.screen == null) mc.setScreen(new ClickGui());
		guiWasDown = gui;

		// Бинды модулей (работают только когда нет открытого экрана)
		if (mc.screen == null) {
			for (Module m : MODULES) {
				boolean down = m.key != -1 && GLFW.glfwGetKey(w, m.key) == GLFW.GLFW_PRESS;
				if (down && !m.wasDown) m.enabled = !m.enabled;
				m.wasDown = down;
			}
			if (TRIGGER.enabled) triggerbot(mc);
		}
	}

	private static void triggerbot(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (mc.gameMode == null) return;

		// цель строго под прицелом (hitResult уже учитывает блоки на пути)
		if (!(mc.hitResult instanceof EntityHitResult hit)) { delay = RNG.nextInt(2); return; }
		Entity e = hit.getEntity();
		if (!(e instanceof LivingEntity target) || target == p) return;
		if (target instanceof ArmorStand || !target.isAlive() || !target.isAttackable()) return;
		if (target.isSpectator() || p.isAlliedTo(target)) return;

		// не бьём, пока щит поднят / едим / тянем лук
		if (p.isUsingItem() || p.isSpectator()) return;

		// только полностью заряженный удар
		if (p.getAttackStrengthScale(0.5f) < 1.0f) return;

		// криты: если мы в воздухе - ждём пока начнём падать
		if (CRITS.enabled && !p.onGround() && !p.onClimbable() && !p.isInWater()
				&& !p.isPassenger() && !p.hasEffect(MobEffects.BLINDNESS)
				&& p.getDeltaMovement().y > 0) return;

		// небольшая задержка после готовности (0-1 тик)
		if (delay-- > 0) return;

		mc.gameMode.attack(p, target);
		p.swing(InteractionHand.MAIN_HAND);
		delay = RNG.nextInt(2);
	}
}
