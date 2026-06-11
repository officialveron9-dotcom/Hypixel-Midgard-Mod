package com.midgard.price;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

/**
 * Auktionshaus-Preise DIREKT im Client (kein Backend/Server). Ein
 * Hintergrund-Thread lädt das komplette AH (alle Seiten) von der öffentlichen
 * Hypixel-API, liest pro Sofortkauf-Auktion (BIN) die SkyBlock-ID aus dem
 * Item-NBT und aggregiert Min/Max/Ø + Lowest BIN je Item. Schwer, aber machbar:
 * Da es auf einem Hintergrund-Thread läuft, ruckelt das Spiel nicht. Aktualisiert
 * ~alle 10 Minuten.
 */
public class AuctionData {

	public static final AuctionData INSTANCE = new AuctionData();

	private static final String URL = "https://api.hypixel.net/v2/skyblock/auctions?page=";
	private static final long REFRESH_MS = 10 * 60_000L;

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private volatile Map<String, double[]> stats = new HashMap<>(); // id -> {min, max, avg, lowestBin}
	private volatile long lastFetchMs = 0;
	private volatile boolean fetching = false;

	public void maybeRefresh() {
		if (fetching) {
			return;
		}
		// Liefert das Backend (prices.json) bereits AH-Statistik, sparen wir uns
		// den schweren Komplett-Download des Auktionshauses auf jedem Client.
		if (PriceApi.INSTANCE.hasAuctions()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!stats.isEmpty() && now - lastFetchMs < REFRESH_MS) {
			return;
		}
		fetching = true;
		lastFetchMs = now;
		CompletableFuture.runAsync(this::fetch);
	}

	private JsonObject getJson(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", "Midgard-Mod (Fabric)")
				.timeout(Duration.ofSeconds(20))
				.GET()
				.build();
		HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) {
			throw new RuntimeException("HTTP " + r.statusCode());
		}
		return JsonParser.parseString(r.body()).getAsJsonObject();
	}

	private void fetch() {
		try {
			JsonObject first = getJson(URL + "0");
			int pages = first.has("totalPages") ? first.get("totalPages").getAsInt() : 1;
			Map<String, List<Double>> byId = new HashMap<>();
			processPage(first, byId);
			for (int pg = 1; pg < pages; pg++) {
				try {
					processPage(getJson(URL + pg), byId);
				} catch (Exception ignored) {
					// einzelne Seite überspringen
				}
			}
			Map<String, double[]> out = new HashMap<>();
			long auctions = 0;
			for (Map.Entry<String, List<Double>> e : byId.entrySet()) {
				List<Double> arr = e.getValue();
				arr.sort(Double::compareTo);
				double sum = 0;
				for (double v : arr) {
					sum += v;
				}
				double min = arr.get(0);
				double max = arr.get(arr.size() - 1);
				out.put(e.getKey(), new double[] { min, max, sum / arr.size(), min });
				auctions += arr.size();
			}
			stats = out;
			System.out.println("[Midgard] AH direkt: " + out.size() + " Items aus " + auctions
					+ " BIN-Auktionen (" + pages + " Seiten)");
		} catch (Exception e) {
			System.err.println("[Midgard] AH-Abruf Fehler: " + e.getMessage());
		} finally {
			fetching = false;
		}
	}

	private void processPage(JsonObject page, Map<String, List<Double>> byId) {
		if (!page.has("auctions")) {
			return;
		}
		JsonArray arr = page.getAsJsonArray("auctions");
		for (JsonElement el : arr) {
			JsonObject a = el.getAsJsonObject();
			if (!a.has("bin") || !a.get("bin").getAsBoolean()) {
				continue; // nur Sofortkauf als Preisreferenz
			}
			if (!a.has("item_bytes") || !a.has("starting_bid")) {
				continue;
			}
			String id = skyblockId(a.get("item_bytes").getAsString());
			if (id == null) {
				continue;
			}
			byId.computeIfAbsent(id, k -> new ArrayList<>()).add(a.get("starting_bid").getAsDouble());
		}
	}

	/** Liest die SkyBlock-Item-ID aus den (base64+gzip) NBT-Bytes einer Auktion. */
	private String skyblockId(String itemBytes) {
		try {
			byte[] data = Base64.getDecoder().decode(itemBytes);
			NbtCompound root = NbtIo.readCompressed(new ByteArrayInputStream(data),
					new NbtSizeTracker(4_000_000L, 64));
			NbtList items = root.getListOrEmpty("i");
			if (items.size() == 0) {
				return null;
			}
			String id = items.getCompoundOrEmpty(0)
					.getCompoundOrEmpty("tag")
					.getCompoundOrEmpty("ExtraAttributes")
					.getString("id", "");
			return id.isEmpty() ? null : id;
		} catch (Exception e) {
			return null;
		}
	}

	/** {min, max, avg, lowestBin} oder null. */
	public double[] get(String id) {
		return stats.get(id);
	}
}
