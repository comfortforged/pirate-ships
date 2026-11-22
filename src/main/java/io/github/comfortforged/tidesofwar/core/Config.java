package io.github.comfortforged.tidesofwar.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server settings from {@code config/tidesofwar.json}. Missing keys keep their defaults, unknown keys are ignored,
 * and the file is rewritten with every key on load so admins can see all the options.
 */
public final class Config {
	public static final class Cost {
		public String item = "minecraft:gunpowder";
		public int count = 1;

		public Cost() {}

		public Cost(String item, int count) {
			this.item = item;
			this.count = count;
		}
	}

	public static final class Cannons {
		public boolean requireAmmo = true;
		public List<Cost> ammo = new ArrayList<>(List.of(new Cost("minecraft:gunpowder", 1)));
		public double reloadSeconds = 5.0;
		public boolean captainCanFire = true;
		public double muzzleSpeed = 2.4;
		public double gravity = 0.05;
		public double spreadDegrees = 1.5;
		public double minElevation = -5;
		public double maxElevation = 25;
		public double traverse = 30;
		public int maxFlightTicks = 200;
	}

	public static final class Damage {
		public double shipDamage = 12.0;
		public double entityDamage = 10.0;
		public double splashRadius = 2.5;
		public boolean pvp = false;
		public boolean mobs = true;
		public boolean blockDamage = true;
		public double explosionPower = 1.6;
		public boolean shipCombat = true;
		public boolean friendlyFire = false;
	}

	public static final class Hull {
		public double sloop = 60;
		public double brigantine = 100;
		public double galleon = 160;
		public String repairItem = "#minecraft:planks";
		public double repairPerItem = 4;
		public int combatLockSeconds = 30;
	}

	public static final class Wrecks {
		/** "anyone" or "owner". */
		public String lootable = "anyone";
		public double raiseHullPercent = 25;
		public Map<String, List<Cost>> raiseCost = defaultRaiseCost();
		public int lifetimeDays = 0;

		private static Map<String, List<Cost>> defaultRaiseCost() {
			Map<String, List<Cost>> m = new LinkedHashMap<>();
			m.put("sloop", new ArrayList<>(List.of(new Cost("#minecraft:planks", 16), new Cost("minecraft:iron_ingot", 4))));
			m.put("brigantine", new ArrayList<>(List.of(new Cost("#minecraft:planks", 32), new Cost("minecraft:iron_ingot", 8))));
			m.put("galleon", new ArrayList<>(List.of(new Cost("#minecraft:planks", 64), new Cost("minecraft:iron_block", 2))));
			return m;
		}
	}

	public Cannons cannons = new Cannons();
	public Damage damage = new Damage();
	public Hull hull = new Hull();
	public Wrecks wrecks = new Wrecks();

	/** The active settings. Never null. */
	public static volatile Config current = new Config();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Path file() {
		return Path.of("config", "tidesofwar.json");
	}

	/** Loads the file (creating it with defaults if missing) and makes it current. */
	public static void load() {
		Path f = file();
		Config c = new Config();
		try {
			if (Files.exists(f)) {
				Config read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Config.class);
				if (read != null) c = read.fillMissing();
			}
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(c), StandardCharsets.UTF_8);
		} catch (Exception e) {
			TidesOfWar.LOGGER.error("Couldn't read {}, using defaults", f, e);
			c = new Config();
		}
		current = c;
	}

	/** Gson leaves a section null if the file wrote null for it. */
	private Config fillMissing() {
		if (cannons == null) cannons = new Cannons();
		if (damage == null) damage = new Damage();
		if (hull == null) hull = new Hull();
		if (wrecks == null) wrecks = new Wrecks();
		if (cannons.ammo == null) cannons.ammo = new ArrayList<>();
		if (wrecks.raiseCost == null) wrecks.raiseCost = new Wrecks().raiseCost;
		return this;
	}

	public double maxHull(ShipType type) {
		return switch (type) {
			case SLOOP -> hull.sloop;
			case BRIGANTINE -> hull.brigantine;
			case GALLEON -> hull.galleon;
		};
	}
}
