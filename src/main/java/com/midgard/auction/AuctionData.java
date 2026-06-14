package com.midgard.auction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Lädt den kompletten aktiven Auktions-Bestand von Hypixel
 * ({@code /v2/skyblock/auctions}, kein API-Key) als schlanken Snapshot und
 * stellt lokale Filter/Sortierung bereit. Muster wie {@link com.midgard.price.PriceApi}:
 * Hintergrund-Threads, {@code volatile}-Swap, Throttle. {@code item_bytes} wird
 * beim Laden einmal dekodiert (Skin/Sterne/ID) und sofort verworfen.
 */
public final class AuctionData {

	public static final AuctionData INSTANCE = new AuctionData();

	private static final String URL = "https://api.hypixel.net/v2/skyblock/auctions?page=";
	private static final long REFRESH_MS = 60_000L;
	private static final long RETRY_MS = 15_000L;
	private static final int POOL = 6;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private volatile List<Auction> all = List.of();
	private volatile long apiLastUpdated = 0;
	private volatile long lastAttemptMs = 0;
	private volatile long lastSuccessMs = 0;
	private volatile boolean fetching = false;
	private volatile boolean browserOpen = false;
	private volatile float progress = 0f;

	private AuctionData() {
	}

	public boolean isLoaded() {
		return !all.isEmpty();
	}

	public int count() {
		return all.size();
	}

	public float progress() {
		return progress;
	}

	public boolean isFetching() {
		return fetching;
	}

	public void setBrowserOpen(boolean open) {
		browserOpen = open;
		if (open) {
			maybeRefresh();
		}
	}

	/** Vom Browser-Screen (Tick) aufgerufen, solange er offen ist. */
	public void maybeRefresh() {
		if (fetching || !browserOpen) {
			return;
		}
		long now = System.currentTimeMillis();
		long wait = lastSuccessMs >= lastAttemptMs ? REFRESH_MS : RETRY_MS;
		if (now - lastAttemptMs < wait) {
			return;
		}
		fetching = true;
		lastAttemptMs = now;
		progress = 0f;
		CompletableFuture.runAsync(this::load);
	}

	private void load() {
		ExecutorService pool = Executors.newFixedThreadPool(POOL);
		try {
			JsonObject first = getPage(0);
			if (first == null) {
				return;
			}
			long updated = first.has("lastUpdated") ? first.get("lastUpdated").getAsLong() : 0;
			int totalPages = first.has("totalPages") ? first.get("totalPages").getAsInt() : 1;
			if (updated != 0 && updated == apiLastUpdated && isLoaded()) {
				lastSuccessMs = System.currentTimeMillis();
				progress = 1f;
				return; // unverändert -> spart Bandbreite
			}

			List<Auction> collected = new ArrayList<>();
			collectPage(first, collected);

			List<CompletableFuture<List<Auction>>> futures = new ArrayList<>();
			for (int p = 1; p < totalPages; p++) {
				final int page = p;
				futures.add(CompletableFuture.supplyAsync(() -> {
					List<Auction> out = new ArrayList<>();
					JsonObject o = getPage(page);
					if (o != null) {
						collectPage(o, out);
					}
					return out;
				}, pool));
			}
			int done = 0;
			for (CompletableFuture<List<Auction>> f : futures) {
				try {
					collected.addAll(f.get());
				} catch (Exception ignored) {
				}
				progress = (float) (++done) / Math.max(1, totalPages);
			}

			all = List.copyOf(collected);
			apiLastUpdated = updated;
			lastSuccessMs = System.currentTimeMillis();
			progress = 1f;
			System.out.println("[Midgard] AuctionData: " + all.size() + " Auktionen ("
					+ totalPages + " Seiten) geladen.");
		} catch (Exception e) {
			System.err.println("[Midgard] AuctionData Laden fehlgeschlagen: " + e.getMessage());
		} finally {
			pool.shutdownNow();
			fetching = false;
		}
	}

	private JsonObject getPage(int page) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(URL + page))
					.header("User-Agent", "Midgard-Mod (Fabric)")
					.timeout(Duration.ofSeconds(20))
					.GET().build();
			HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() != 200) {
				return null;
			}
			return JsonParser.parseString(r.body()).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	private void collectPage(JsonObject page, List<Auction> out) {
		if (!page.has("auctions")) {
			return;
		}
		JsonArray arr = page.getAsJsonArray("auctions");
		for (int i = 0; i < arr.size(); i++) {
			try {
				out.add(toAuction(arr.get(i).getAsJsonObject()));
			} catch (Exception ignored) {
			}
		}
	}

	private static Auction toAuction(JsonObject o) {
		String itemName = str(o, "item_name");
		String tier = str(o, "tier");
		String category = str(o, "category");
		long start = lng(o, "starting_bid");
		long high = lng(o, "highest_bid_amount");
		long end = lng(o, "end");
		boolean bin = o.has("bin") && o.get("bin").getAsBoolean();
		int bids = o.has("bids") && o.get("bids").isJsonArray() ? o.getAsJsonArray("bids").size() : 0;

		AuctionItemParser.Parsed p = AuctionItemParser.parse(str(o, "item_bytes"));
		int stars = p.stars();
		if (stars == 0) {
			stars = countStars(itemName); // Fallback aus dem Namen (✪)
		}
		return new Auction(itemName, strip(itemName).toLowerCase(Locale.ROOT), p.internalId(),
				category, tier, start, high, end, bin, stars, p.skinValue(), bids);
	}

	private static long lng(JsonObject o, String k) {
		try {
			return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	/** §-Codes entfernen. */
	static String strip(String s) {
		return s == null ? "" : s.replaceAll("(?i)\\u00a7[0-9a-fk-or]", "");
	}

	/** Sterne aus dem Namen zählen (✪ und rote Master-Stern-Symbole). */
	private static int countStars(String name) {
		String s = strip(name);
		int c = 0;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '✪' || (ch >= '➊' && ch <= '➓')) { // ✪ und ➊..➓
				c++;
			}
		}
		return Math.min(10, c);
	}

	// ---- Abfrage (lokal) --------------------------------------------------

	/** Gefilterte + sortierte Auktionen für eine Filter-Einstellung. */
	public List<Auction> query(AuctionFilter f) {
		List<Auction> src = all;
		List<Auction> out = new ArrayList<>();
		for (Auction a : src) {
			if (f.matches(a)) {
				out.add(a);
			}
		}
		Comparator<Auction> cmp = switch (f.sort()) {
			case HIGHEST -> Comparator.comparingLong(Auction::price).reversed();
			case ENDING -> Comparator.comparingLong(Auction::end);
			case BIDS -> Comparator.comparingInt(Auction::bidCount).reversed();
			default -> Comparator.comparingLong(Auction::price); // LOWEST
		};
		out.sort(cmp);
		return out;
	}
}
