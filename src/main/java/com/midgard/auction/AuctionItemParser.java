package com.midgard.auction;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

/**
 * Entschlüsselt das {@code item_bytes}-Feld einer Hypixel-Auktion
 * (base64 → gzip → NBT) und zieht nur die nötigen Felder heraus: interne ID,
 * Sternanzahl und die Kopf-Skin-Textur. Baut daraus ein echtes Kopf-Icon
 * (gecacht pro Skin, da sich Köpfe tausendfach wiederholen).
 */
public final class AuctionItemParser {

	/** Ergebnis der NBT-Extraktion. */
	public record Parsed(String internalId, int stars, String skinValue) {
	}

	private static final Parsed EMPTY = new Parsed("", 0, "");
	private static final java.util.Map<String, ItemStack> skullCache = new ConcurrentHashMap<>();

	private AuctionItemParser() {
	}

	/** item_bytes -> {internalId, stars, skinValue}. Nie null; bei Fehler leer. */
	public static Parsed parse(String itemBytes) {
		if (itemBytes == null || itemBytes.isEmpty()) {
			return EMPTY;
		}
		try {
			byte[] raw = Base64.getDecoder().decode(itemBytes);
			NbtCompound root = NbtIo.readCompressed(new ByteArrayInputStream(raw), NbtSizeTracker.of(0x4000000L));
			NbtList list = root.getListOrEmpty("i");
			if (list.isEmpty() || !(list.get(0) instanceof NbtCompound item)) {
				return EMPTY;
			}
			NbtCompound tag = item.getCompoundOrEmpty("tag");
			NbtCompound extra = tag.getCompoundOrEmpty("ExtraAttributes");
			String id = extra.getString("id", "");
			int stars = extra.getInt("upgrade_level", extra.getInt("dungeon_item_level", 0));
			String skin = "";
			NbtCompound owner = tag.getCompoundOrEmpty("SkullOwner");
			NbtCompound props = owner.getCompoundOrEmpty("Properties");
			NbtList textures = props.getListOrEmpty("textures");
			if (!textures.isEmpty() && textures.get(0) instanceof NbtCompound t0) {
				skin = t0.getString("Value", "");
			}
			return new Parsed(id, Math.max(0, Math.min(10, stars)), skin);
		} catch (Throwable t) {
			return EMPTY;
		}
	}

	/** Kopf-ItemStack aus der base64-Textur (gecacht). Null, wenn nicht baubar. */
	public static ItemStack head(String skinValue) {
		if (skinValue == null || skinValue.isEmpty()) {
			return null;
		}
		ItemStack cached = skullCache.get(skinValue);
		if (cached != null) {
			return cached;
		}
		ItemStack built = buildHead(skinValue);
		if (built != null) {
			if (skullCache.size() > 4000) {
				skullCache.clear(); // einfache Obergrenze gegen unbegrenztes Wachsen
			}
			skullCache.put(skinValue, built);
		}
		return built;
	}

	private static boolean loggedHeadError = false;

	private static ItemStack buildHead(String value) {
		try {
			Multimap<String, Property> mm = LinkedHashMultimap.create();
			mm.put("textures", new Property("textures", value));
			GameProfile profile = new GameProfile(
					UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), "MidgardHead", new PropertyMap(mm));
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(profile));
			return head;
		} catch (Throwable t) {
			if (!loggedHeadError) {
				loggedHeadError = true;
				System.err.println("[Midgard] Kopf-Bau fehlgeschlagen (Skins -> Papier): " + t);
			}
			return null;
		}
	}
}
