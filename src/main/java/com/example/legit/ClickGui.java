package com.example.legit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class ClickGui extends Screen {
	private final Map<Module, Button> toggles = new HashMap<>();
	private final Map<Module, Button> binds = new HashMap<>();
	private Module listening = null;
	private boolean rshiftWasDown = true; // открыли по Right Shift, ждём отпускания

	public ClickGui() { super(Component.literal("LegitClient")); }

	@Override
	protected void init() {
		toggles.clear();
		binds.clear();
		int cx = this.width / 2, y = this.height / 2 - 40;
		for (Module m : LegitClient.MODULES) {
			Button toggle = Button.builder(Component.empty(), btn -> { m.enabled = !m.enabled; refresh(); })
					.bounds(cx - 105, y, 120, 20).build();
			Button bind = Button.builder(Component.empty(), btn -> { listening = m; refresh(); })
					.bounds(cx + 20, y, 85, 20).build();
			toggles.put(m, toggle);
			binds.put(m, bind);
			addRenderableWidget(toggle);
			addRenderableWidget(bind);
			y += 24;
		}
		refresh();
	}

	private void refresh() {
		for (Module m : LegitClient.MODULES) {
			Button t = toggles.get(m), b = binds.get(m);
			if (t == null || b == null) continue;
			t.setMessage(Component.literal(m.name + ": " + (m.enabled ? "ON" : "OFF")));
			String k = listening == m ? "..." : keyName(m.key);
			b.setMessage(Component.literal("Bind: " + k));
		}
	}

	private static String keyName(int key) {
		if (key == -1) return "NONE";
		String n = GLFW.glfwGetKeyName(key, 0);
		return n != null ? n.toUpperCase() : "KEY_" + key;
	}

	@Override
	public void tick() {
		long w = GLFW.glfwGetCurrentContext();
		if (w == 0L) return;

		boolean rs = GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		if (rs && !rshiftWasDown && listening == null) { onClose(); return; }
		rshiftWasDown = rs;

		if (listening != null) {
			for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
				if (k == GLFW.GLFW_KEY_RIGHT_SHIFT) continue;
				if (GLFW.glfwGetKey(w, k) == GLFW.GLFW_PRESS) {
					if (k == GLFW.GLFW_KEY_ESCAPE) { /* отмена */ }
					else if (k == GLFW.GLFW_KEY_BACKSPACE) listening.key = -1;
					else listening.key = k;
					listening.wasDown = true; // чтобы не сработал сразу
					listening = null;
					refresh();
					break;
				}
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawCenteredString(this.font, "LegitClient  |  Right Shift - закрыть  |  Backspace - снять бинд",
				width / 2, height / 2 - 65, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
