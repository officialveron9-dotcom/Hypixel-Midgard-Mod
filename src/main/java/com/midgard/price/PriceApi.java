package com.midgard.price;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.midgard.Midgard;

/**
 * Einzige Datenquelle des Mods für Preise + Jacob-Zeitplan: die aggregierte
 * {@code prices.json} vom eigenen Backend (GitHub Pages). Die Clients reden
 * NIE direkt mit Hypixel oder elitebot — nur das Backend tut das (1x zentral).
 * Ist das Backend nicht erreichbar, meldet {@link #isOnline()} false und der
 * Mod zeigt "Keine Backend-Verbindung" an.
 */
public class PriceApi {

	public static final PriceApi INSTANCE = new PriceApi();
	public static final long CONTEST_SECONDS = 20 * 60;

	private static final long REFRESH_MS = 5 * 60_000L;
	private static final long RETRY_MS = 60_000L; // schnellerer Neuversuch nach Fehler
	private static final long ONLINE_GRACE_MS = 15 * 60_000L;

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private volatile Map<String, double[]> bazaar = new HashMap<>(); // id -> {buy, sell}
	private volatile Map<String, double[]> auctions = new HashMap<>(); // id -> {min, max, avg, lowestBin}
	private volatile TreeMap<Long, List<String>> jacob = new TreeMap<>(); // startSec -> crops
	private volatile long lastAttemptMs = 0;
	private volatile long lastSuccessMs = 0;
	private volatile boolean fetching = false;
	private volatile String lastUrl = "";

	public void maybeRefresh() {
		String url = Midgard.config == null ? null : Midgard.config.priceApiUrl;
		if (url == null || url.isBlank()) {
			return;
		}
		if (fetching) {
			return;
		}
		long now = System.currentTimeMillis();
		boolean urlChanged = !url.equals(lastUrl);
		long wait = lastSuccessMs >= lastAttemptMs ? REFRESH_MS : RETRY_MS;
		if (!urlChanged && now - lastAttemptMs < wait) {
			return;
		}
		fetching = true;
		lastAttemptMs = now;
		lastUrl = url;
		final String u = url;
		CompletableFuture.runAsync(() -> fetch(u));
	}

	private void fetch(String url) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "Midgard-Mod (Fabric)")
					.timeout(Duration.ofSeconds(15))
					.GET()
					.build();
			HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() != 200) {
				System.err.println("[Midgard] Backend HTTP " + r.statusCode());
				return;
			}
			JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();

			Map<String, double[]> bz = new HashMap<>();
			if (root.has("bazaar")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("bazaar").entrySet()) {
					JsonObject o = e.getValue().getAsJsonObject();
					bz.put(e.getKey(), new double[] { d(o, "buy"), d(o, "sell") });
				}
			}
			Map<String, double[]> au = new HashMap<>();
			if (root.has("auctions")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("auctions").entrySet()) {
					JsonObject o = e.getValue().getAsJsonObject();
					au.put(e.getKey(), new double[] { d(o, "min"), d(o, "max"), d(o, "avg"), d(o, "lowestBin") });
				}
			}
			TreeMap<Long, List<String>> jc = new TreeMap<>();
			if (root.has("jacob")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("jacob").entrySet()) {
					try {
						long t = Long.parseLong(e.getKey());
						List<String> crops = new ArrayList<>();
						for (JsonElement c : e.getValue().getAsJsonArray()) {
							crops.add(c.getAsString());
						}
						if (!crops.isEmpty()) {
							jc.put(t, crops);
						}
					} catch (Exception ignored) {
					}
				}
			}
			bazaar = bz;
			auctions = au;
			jacob = jc;
			lastSuccessMs = System.currentTimeMillis();
		} catch (Exception e) {
			System.err.println("[Midgard] Keine Backend-Verbindung: " + e.getMessage());
		} finally {
			fetching = false;
		}
	}

	private static double d(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
	}

	/**
	 * true, solange die letzte erfolgreiche Backend-Abfrage nicht zu lange her
	 * ist. false = (noch) keine Backend-Verbindung.
	 */
	public boolean isOnline() {
		return lastSuccessMs > 0 && System.currentTimeMillis() - lastSuccessMs < ONLINE_GRACE_MS;
	}

	/** {buy, sell} oder null, wenn das Item nicht im Bazaar ist. */
	public double[] bazaar(String id) {
		return bazaar.get(id);
	}

	/** {min, max, avg, lowestBin} oder null. */
	public double[] auction(String id) {
		return auctions.get(id);
	}

	public boolean hasJacob() {
		return !jacob.isEmpty();
	}

	/** Laufender Contest (start, crops) oder null. */
	public Map.Entry<Long, List<String>> jacobActive(long nowSec) {
		Map.Entry<Long, List<String>> e = jacob.floorEntry(nowSec);
		return (e != null && nowSec < e.getKey() + CONTEST_SECONDS) ? e : null;
	}

	/** Die nächsten {@code count} Contests (start, crops). */
	public List<Map.Entry<Long, List<String>>> jacobUpcoming(long nowSec, int count) {
		List<Map.Entry<Long, List<String>>> out = new ArrayList<>();
		for (Map.Entry<Long, List<String>> e : jacob.tailMap(nowSec + 1, true).entrySet()) {
			if (out.size() >= count) {
				break;
			}
			out.add(e);
		}
		return out;
	}
}
