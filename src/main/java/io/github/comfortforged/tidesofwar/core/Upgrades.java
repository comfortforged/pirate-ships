package io.github.comfortforged.tidesofwar.core;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** The upgrade tiers installed on one ship. All zero (and no flight core) is a stock ship. */
public final class Upgrades {
	public enum Part {
		HULL("hull", "Hull Plating", 3),
		SAILS("sails", "Sails", 3),
		RUDDER("rudder", "Rudder", 3),
		CANNONS("cannons", "Cannon Upgrade", 3),
		FLIGHT("flight", "Flight Core", 1);

		public final String id;
		public final String displayName;
		public final int maxTier;

		Part(String id, String displayName, int maxTier) {
			this.id = id;
			this.displayName = displayName;
			this.maxTier = maxTier;
		}

		/** The vanilla item the upgrade item is built on (one that does nothing when right-clicked). */
		public Item baseItem() {
			return switch (this) {
				case HULL -> Items.PRISMARINE_SHARD;
				case SAILS -> Items.PAPER;
				case RUDDER -> Items.PRISMARINE_CRYSTALS;
				case CANNONS -> Items.FLINT;
				case FLIGHT -> Items.HEART_OF_THE_SEA;
			};
		}

		public static Part byId(String id) {
			for (Part p : values()) {
				if (p.id.equals(id)) return p;
			}
			return null;
		}

		/** "Sails II", or "Flight Core". */
		public String nameOfTier(int tier) {
			return maxTier == 1 ? displayName : displayName + " " + roman(tier);
		}
	}

	public int hull, sails, rudder, cannons;
	public boolean flight;

	public static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> String.valueOf(n);
		};
	}

	public int get(Part p) {
		return switch (p) {
			case HULL -> hull;
			case SAILS -> sails;
			case RUDDER -> rudder;
			case CANNONS -> cannons;
			case FLIGHT -> flight ? 1 : 0;
		};
	}

	public void set(Part p, int tier) {
		int t = Math.max(0, Math.min(p.maxTier, tier));
		switch (p) {
			case HULL -> hull = t;
			case SAILS -> sails = t;
			case RUDDER -> rudder = t;
			case CANNONS -> cannons = t;
			case FLIGHT -> flight = t > 0;
		}
	}

	public boolean isStock() {
		return hull == 0 && sails == 0 && rudder == 0 && cannons == 0 && !flight;
	}

	public Upgrades copy() {
		Upgrades u = new Upgrades();
		for (Part p : Part.values()) u.set(p, get(p));
		return u;
	}

	public boolean sameAs(Upgrades o) {
		for (Part p : Part.values()) {
			if (get(p) != o.get(p)) return false;
		}
		return true;
	}

	/** Short form for lists: "H3 S2 R0 C1 F" (F only with a flight core). */
	public String summary() {
		return "H" + hull + " S" + sails + " R" + rudder + " C" + cannons + (flight ? " F" : "");
	}

	/** Readable lines for item lore and messages, installed parts only. */
	public List<String> describe() {
		List<String> out = new ArrayList<>();
		for (Part p : Part.values()) {
			int t = get(p);
			if (t > 0) out.add(p.nameOfTier(t));
		}
		return out;
	}

	// ------------------------------------------------------------------ saving

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		for (Part p : Part.values()) o.addProperty(p.id, get(p));
		return o;
	}

	public static Upgrades fromJson(JsonObject o) {
		Upgrades u = new Upgrades();
		if (o == null) return u;
		for (Part p : Part.values()) {
			if (o.has(p.id)) u.set(p, o.get(p.id).getAsInt());
		}
		return u;
	}

	private static String key(Part p) {
		return "tidesofwar_up_" + p.id;
	}

	/** Writes the installed tiers into an item's custom data tag. */
	public void toTag(CompoundTag tag) {
		for (Part p : Part.values()) {
			if (get(p) > 0) tag.putInt(key(p), get(p));
		}
	}

	public static Upgrades fromTag(CompoundTag tag) {
		Upgrades u = new Upgrades();
		for (Part p : Part.values()) u.set(p, tag.getIntOr(key(p), 0));
		return u;
	}
}
