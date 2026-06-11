package com.midgard.price;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Holt die Bazaar-Preise DIREKT von der öffentlichen Hypixel-API (kein API-Key
 * nötig). Läuft pro Client – ein kleiner Abruf alle paar Minuten, asynchron,
 * gecacht. Kein Server, kein öffentliches Repo. Liefert pro SkyBlock-Item-ID
 * den Sofortkauf- (buy) und Sofortverkauf-Preis (sell).
 */
public class BazaarData {

	public static final BazaarData INSTANCE = new BazaarData();

	private static final String URL = "https://api.hypixel.net/v2/skyblock/bazaar";
	private static final long REFRESH_MS = 2 * 60_000L;

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	/** SkyBlock-ID -> {buyPrice, sellPrice} */
	private volatile Map<String, double[]> prices = new HashMap<>();
	private volatile long lastFetchMs = 0;
	private volatile boolean fetching = false;

	public void maybeRefresh() {
		if (fetching) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!prices.isEmpty() && now - lastFetchMs < REFRESH_MS) {
			return;
		}
		fetching = true;
		lastFetchMs = now;
		CompletableFuture.runAsync(this::fetch);
	}

	private void fetch() {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
					.header("User-Agent", "Midgard-Mod (Fabric)")
					.timeout(Duration.ofSeconds(15))
					.GET()
					.build();
			HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				return;
			}
			JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
			JsonObject products = root.getAsJsonObject("products");
			if (products == null) {
				return;
			}
			Map<String, double[]> parsed = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : products.entrySet()) {
				JsonObject p = e.getValue().getAsJsonObject();
				JsonObject q = p.getAsJsonObject("quick_status");
				if (q == null) {
					continue;
				}
				double buy = q.has("buyPrice") ? q.get("buyPrice").getAsDouble() : 0;
				double sell = q.has("sellPrice") ? q.get("sellPrice").getAsDouble() : 0;
				parsed.put(e.getKey(), new double[] { buy, sell });
			}
			if (!parsed.isEmpty()) {
				prices = parsed;
			}
		} catch (Exception e) {
			System.err.println("[Midgard] Bazaar-Abruf Fehler: " + e.getMessage());
		} finally {
			fetching = false;
		}
	}

	public boolean hasData() {
		return !prices.isEmpty();
	}

	/** {buy, sell} oder null, wenn das Item nicht im Bazaar ist. */
	public double[] get(String skyblockId) {
		return prices.get(skyblockId);
	}
}
