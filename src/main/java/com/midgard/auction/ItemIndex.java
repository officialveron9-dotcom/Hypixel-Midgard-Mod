package com.midgard.auction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;

/**
 * Lädt einmalig die SkyBlock-Item-Liste (Name + ID + Material) von der
 * öffentlichen Hypixel-Ressource und hält sie für die Auktions-Suche bereit.
 * Muster wie {@link com.midgard.price.PriceApi}: HTTP async, {@code volatile}
 * Liste, plus ein Disk-Cache, damit die Vorschläge sofort da sind.
 */
public final class ItemIndex {

	public static final ItemIndex INSTANCE = new ItemIndex();

	private static final String URL = "https://api.hypixel.net/resources/skyblock/items";
	private static final long MAX_CACHE_AGE_MS = 24L * 60 * 60 * 1000; // 1 Tag

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private volatile List<ItemEntry> items = List.of();
	private volatile boolean loading = false;
	private volatile boolean triedThisSession = false;
	private final Map<String, ItemStack> iconCache = new ConcurrentHashMap<>();

	private ItemIndex() {
	}

	/** Gebautes Anzeige-Item (einmal pro Item-ID gebaut, dann gecacht). */
	public ItemStack iconFor(ItemEntry e) {
		String key = e.id() != null ? e.id() : e.name();
		ItemStack cached = iconCache.get(key);
		if (cached == null) {
			cached = e.buildIcon();
			iconCache.put(key, cached);
		}
		return cached;
	}

	public boolean isLoaded() {
		return !items.isEmpty();
	}

	public int size() {
		return items.size();
	}

	/** Beim ersten Bedarf: Cache sofort laden, dann ggf. im Hintergrund auffrischen. */
	public void ensureLoaded() {
		if (triedThisSession || loading) {
			return;
		}
		triedThisSession = true;
		loading = true;
		CompletableFuture.runAsync(this::loadFromCacheThenNetwork);
	}

	private Path cacheFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("midgard_items.json");
	}

	private void loadFromCacheThenNetwork() {
		try {
			Path cache = cacheFile();
			boolean fresh = false;
			if (Files.exists(cache)) {
				try {
					String raw = Files.readString(cache);
					List<ItemEntry> parsed = parse(raw);
					if (!parsed.isEmpty()) {
						items = parsed;
						long age = System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis();
						fresh = age < MAX_CACHE_AGE_MS;
						System.out.println("[Midgard] ItemIndex: " + parsed.size() + " Items aus Cache geladen.");
					}
				} catch (Exception e) {
					System.err.println("[Midgard] ItemIndex Cache defekt: " + e.getMessage());
				}
			}
			if (!fresh) {
				fetchFromNetwork(cache);
			}
		} finally {
			loading = false;
		}
	}

	private void fetchFromNetwork(Path cache) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
					.header("User-Agent", "Midgard-Mod (Fabric)")
					.timeout(Duration.ofSeconds(15))
					.GET()
					.build();
			HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() != 200) {
				System.err.println("[Midgard] ItemIndex HTTP " + r.statusCode());
				return;
			}
			List<ItemEntry> parsed = parse(r.body());
			if (!parsed.isEmpty()) {
				items = parsed;
				try {
					Files.writeString(cache, r.body());
				} catch (Exception e) {
					System.err.println("[Midgard] ItemIndex Cache speichern fehlgeschlagen: " + e.getMessage());
				}
				System.out.println("[Midgard] ItemIndex: " + parsed.size() + " Items von Hypixel geladen.");
			}
		} catch (Exception e) {
			System.err.println("[Midgard] ItemIndex Laden fehlgeschlagen: " + e.getMessage());
		}
	}

	private static List<ItemEntry> parse(String body) {
		List<ItemEntry> out = new ArrayList<>();
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		if (!root.has("items")) {
			return out;
		}
		JsonArray arr = root.getAsJsonArray("items");
		for (int i = 0; i < arr.size(); i++) {
			JsonObject o = arr.get(i).getAsJsonObject();
			String name = str(o, "name");
			if (name.isEmpty()) {
				continue;
			}
			out.add(new ItemEntry(str(o, "id"), name, str(o, "material"), str(o, "tier"),
					str(o, "category"), skinValue(o), statsMap(o), dbl(o, "npc_sell_price")));
		}
		return out;
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	private static double dbl(JsonObject o, String k) {
		try {
			return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	/** Kopf-Textur (base64) – kann String oder Objekt mit "value" sein. */
	private static String skinValue(JsonObject o) {
		try {
			if (!o.has("skin") || o.get("skin").isJsonNull()) {
				return "";
			}
			JsonElement s = o.get("skin");
			if (s.isJsonObject() && s.getAsJsonObject().has("value")) {
				return s.getAsJsonObject().get("value").getAsString();
			}
			if (s.isJsonPrimitive()) {
				return s.getAsString();
			}
		} catch (Exception ignored) {
		}
		return "";
	}

	/** Stats-Map (z. B. DAMAGE -> 100) oder leer. */
	private static Map<String, Double> statsMap(JsonObject o) {
		if (!o.has("stats") || !o.get("stats").isJsonObject()) {
			return Map.of();
		}
		Map<String, Double> m = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("stats").entrySet()) {
			try {
				if (e.getValue().isJsonPrimitive()) {
					m.put(e.getKey(), e.getValue().getAsDouble());
				}
			} catch (Exception ignored) {
			}
		}
		return m;
	}

	/**
	 * Vorschläge zur Eingabe (lokal, ohne Netz): Prefix &gt; Wort-Prefix &gt;
	 * Teilstring, dann alphabetisch. Leere Eingabe = leer (der Screen zeigt dann
	 * den Verlauf). Maximal {@code limit} Treffer.
	 */
	public List<ItemEntry> suggest(String query, int limit) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return List.of();
		}
		List<ItemEntry> src = items;
		List<ItemEntry> ranked = new ArrayList<>();
		List<Integer> scores = new ArrayList<>();
		for (ItemEntry e : src) {
			String n = e.name().toLowerCase(Locale.ROOT);
			int score;
			if (n.startsWith(q)) {
				score = 0;
			} else if (wordStartsWith(n, q)) {
				score = 1;
			} else if (n.contains(q)) {
				score = 2;
			} else {
				continue;
			}
			ranked.add(e);
			scores.add(score);
		}
		// nach Score, dann Name sortieren (stabil über Index-Sort)
		List<Integer> idx = new ArrayList<>();
		for (int i = 0; i < ranked.size(); i++) {
			idx.add(i);
		}
		idx.sort((a, b) -> {
			int s = Integer.compare(scores.get(a), scores.get(b));
			if (s != 0) {
				return s;
			}
			return ranked.get(a).name().compareToIgnoreCase(ranked.get(b).name());
		});
		List<ItemEntry> out = new ArrayList<>();
		for (int i = 0; i < idx.size() && out.size() < limit; i++) {
			out.add(ranked.get(idx.get(i)));
		}
		return out;
	}

	private static boolean wordStartsWith(String name, String q) {
		int i = name.indexOf(' ');
		while (i >= 0) {
			if (name.startsWith(q, i + 1)) {
				return true;
			}
			i = name.indexOf(' ', i + 1);
		}
		return false;
	}
}
