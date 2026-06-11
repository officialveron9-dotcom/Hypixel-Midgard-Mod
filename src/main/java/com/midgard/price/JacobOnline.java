package com.midgard.price;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Holt den vollen Jacob-Zeitplan DIREKT von elitebot.dev (Fallback, wenn kein
 * eigenes Backend gesetzt ist). Quellenangabe ist im Menü verlinkt. Asynchron,
 * 15-Min-Cache. So funktioniert "Jacob 3–5" sofort ohne eigenen Server – für
 * sehr viele Nutzer ist später das eigene Backend schonender (1 Abruf statt
 * pro Client), aber zum Loslegen reicht das hier.
 */
public class JacobOnline implements JacobSource {

	public static final JacobOnline INSTANCE = new JacobOnline();

	private static final String URL = "https://api.elitebot.dev/contests/at/now";
	private static final long REFRESH_MS = 15 * 60_000L;

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private volatile TreeMap<Long, List<String>> jacob = new TreeMap<>();
	private volatile long lastFetchMs = 0;
	private volatile boolean fetching = false;

	public void maybeRefresh() {
		if (fetching) {
			return;
		}
		long now = System.currentTimeMillis();
		boolean stale = now - lastFetchMs > REFRESH_MS;
		boolean exhausted = jacob.isEmpty() || jacob.lastKey() < now / 1000;
		if (!stale && !exhausted) {
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
			HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() != 200) {
				return;
			}
			JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
			if (!root.has("contests")) {
				return;
			}
			TreeMap<Long, List<String>> jc = new TreeMap<>();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("contests").entrySet()) {
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
			if (!jc.isEmpty()) {
				jacob = jc;
			}
		} catch (Exception e) {
			System.err.println("[Midgard] Jacob (elitebot) Fehler: " + e.getMessage());
		} finally {
			fetching = false;
		}
	}

	@Override
	public boolean hasJacob() {
		return !jacob.isEmpty();
	}

	@Override
	public Map.Entry<Long, List<String>> jacobActive(long nowSec) {
		Map.Entry<Long, List<String>> e = jacob.floorEntry(nowSec);
		return (e != null && nowSec < e.getKey() + PriceApi.CONTEST_SECONDS) ? e : null;
	}

	@Override
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
