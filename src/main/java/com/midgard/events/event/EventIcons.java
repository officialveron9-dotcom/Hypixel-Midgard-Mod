package com.midgard.events.event;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Ordnet Events, Kategorien und Crops ein passendes Minecraft-Item als Icon zu. */
public final class EventIcons {

	private EventIcons() {
	}

	public static Item forEvent(EventType type) {
		return switch (type) {
			case JACOB_CONTEST -> Items.WHEAT;
			case SPOOKY_FESTIVAL -> Items.JACK_O_LANTERN;
			case NEW_YEAR_CELEBRATION -> Items.CAKE;
			case SEASON_OF_JERRY -> Items.SNOW_BLOCK;
			case TRAVELING_ZOO -> Items.HAY_BLOCK;
			case DARK_AUCTION -> Items.EMERALD;
			case MINING_FIESTA -> Items.DIAMOND_PICKAXE;
			case MINING_EVENT -> Items.IRON_PICKAXE;
			case FISHING_FESTIVAL -> Items.FISHING_ROD;
			case MAYOR_ELECTION -> Items.PAPER;
		};
	}

	public static Item forCategory(EventCategory category) {
		return switch (category) {
			case CALENDAR -> Items.CLOCK;
			case JACOB -> Items.WHEAT;
			case MINING -> Items.DIAMOND_PICKAXE;
			case FISHING -> Items.FISHING_ROD;
			case OTHER -> Items.BOOK;
		};
	}

	/** Crop-Name (von elitebot.dev) -> Item. */
	public static Item forCrop(String name) {
		String n = name.toLowerCase();
		if (n.contains("wheat")) return Items.WHEAT;
		if (n.contains("carrot")) return Items.CARROT;
		if (n.contains("potato")) return Items.POTATO;
		if (n.contains("pumpkin")) return Items.PUMPKIN;
		if (n.contains("melon")) return Items.MELON;
		if (n.contains("sugar")) return Items.SUGAR_CANE;
		if (n.contains("cactus")) return Items.CACTUS;
		if (n.contains("cocoa")) return Items.COCOA_BEANS;
		if (n.contains("mushroom")) return Items.RED_MUSHROOM;
		if (n.contains("wart")) return Items.NETHER_WART;
		if (n.contains("sunflower")) return Items.SUNFLOWER;
		if (n.contains("moonflower")) return Items.CORNFLOWER;
		if (n.contains("rose")) return Items.ROSE_BUSH;
		return Items.WHEAT;
	}
}
