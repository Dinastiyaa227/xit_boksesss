package com.example.legit;

public class Module {
	public final String name;
	public boolean enabled = false;
	public int key = -1;      // GLFW код клавиши, -1 = не назначена
	public boolean wasDown = false;

	public Module(String name) { this.name = name; }
}
