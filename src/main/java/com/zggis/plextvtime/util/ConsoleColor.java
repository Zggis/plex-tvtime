package com.zggis.plextvtime.util;

public enum ConsoleColor {
	YELLOW("\u001B[33m"), RED("\u001B[31m"), GREEN("\u001B[32m"), BLUE("\u001B[34m"), NONE("\u001B[0m"),
	CYAN("\u001B[36m"), MAGENTA("\u001B[95m");

	public final String value;

	ConsoleColor(String label) {
		this.value = label;
	}
}
