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
 * Lädt die aggregierte {@code prices.json} vom (selbst gehosteten) Backend:
 * Auktionshaus-Statistik (Min/Max/Ø/Lowest BIN pro Item) und den vollen
 * Jacob-Zeitplan. Wird nur genutzt, wenn eine URL gesetzt ist; schlägt der
 * Abruf fehl (Backend noch nicht online), bleibt alles leer und der Mod nutzt
 * weiter die direkten Bazaar-Preise + den In-Game-Jacob.
 */
public class PriceApi implements JacobSource {

	public static final PriceApi INSTANCE = new PriceApi();
	public static final long CONTEST_SECONDS = 20 * 60;

	private static final long REFRESH_MS = 5 * 60_000L;

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private volatile Map<String, double[]> auctions = new HashMap<>(); // id -> {min, max, avg, lowestBin}
	private volatile TreeMap<Long, List<String>> jacob = new TreeMap<>(); // startSec -> crops
	private volatile long lastFetchMs = 0;
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
		boolean haveData = !auctions.isEmpty() || !jacob.isEmpty();
		if (!urlChanged && haveData && now - lastFetchMs < REFRESH_MS) {
			return;
		}
		fetching = true;
		lastFetchMs = now;
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
				return;
			}
			JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();

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
			auctions = au;
			jacob = jc;
		} catch (Exception e) {
			System.err.println("[Midgard] Preis-API Fehler: " + e.getMessage());
		} finally {
			fetching = false;
		}
	}

	private static double d(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
	}

	/** {min, max, avg, lowestBin} oder null. */
	public double[] auction(String id) {
		return auctions.get(id);
	}

	public boolean hasAuctions() {
		return !auctions.isEmpty();
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
