package com.midgard.events.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.skyblock.SkyblockCalendar;
import com.midgard.events.skyblock.SkyblockHook;

/**
 * Stellt aus Jacob-API, live erkannten Events und Kalender-Events die fertige,
 * sortierte Liste zusammen, die das HUD anzeigt. Wird regelmäßig
 * aktualisiert; das HUD liest nur das zwischengespeicherte Ergebnis.
 */
public class EventManager {

	public static final EventManager INSTANCE = new EventManager();

	private volatile List<EventDisplay> current = new ArrayList<>();

	public List<EventDisplay> get() {
		return current;
	}

	public void update() {
		List<EventDisplay> list = new ArrayList<>();
		ModConfig cfg = Midgard.config;
		SkyblockCalendar cal = SkyblockCalendar.INSTANCE;

		// 1) Jacob's Contest. Quelle: eigenes Backend (prices.json) bevorzugt,
		//    sonst direkt elitebot (voller Jahresplan → 3–5 sofort), sonst der
		//    In-Game-Plan (~2). Alle drei liefern den vollen Plan außer In-Game.
		com.midgard.price.JacobSource jsrc = !cfg.isEventEnabled(EventType.JACOB_CONTEST) ? null
				: com.midgard.price.PriceApi.INSTANCE.hasJacob() ? com.midgard.price.PriceApi.INSTANCE
				: com.midgard.price.JacobOnline.INSTANCE.hasJacob() ? com.midgard.price.JacobOnline.INSTANCE
				: null;
		if (jsrc != null) {
			long nowSec = System.currentTimeMillis() / 1000;
			int n = cfg.getUpcomingEvent(EventType.JACOB_CONTEST);
			java.util.Map.Entry<Long, List<String>> act = jsrc.jacobActive(nowSec);
			if (act != null) {
				list.add(new EventDisplay(EventType.JACOB_CONTEST, "Jacob's Contest", true,
						act.getKey() + com.midgard.price.PriceApi.CONTEST_SECONDS - nowSec, act.getValue()));
			}
			for (java.util.Map.Entry<Long, List<String>> e : jsrc.jacobUpcoming(nowSec, n)) {
				list.add(new EventDisplay(EventType.JACOB_CONTEST, "Jacob's Contest", false,
						e.getKey() - nowSec, e.getValue()));
			}
		} else if (cfg.isEventEnabled(EventType.JACOB_CONTEST) && cal.isValid()) {
			int n = cfg.getUpcomingEvent(EventType.JACOB_CONTEST);
			double horizon = 30 * SkyblockCalendar.REAL_SECONDS_PER_SB_DAY;
			List<double[]> upStarts = new ArrayList<>();
			List<List<String>> upCrops = new ArrayList<>();
			for (JacobSchedule.Entry e : JacobSchedule.INSTANCE.entries()) {
				double ar = cal.realSecondsActiveRemaining(e.month(), e.day(), 1);
				if (ar >= 0) {
					list.add(new EventDisplay(EventType.JACOB_CONTEST, "Jacob's Contest", true, ar, e.crops()));
					continue;
				}
				double start = cal.realSecondsUntilStart(e.month(), e.day());
				if (start > 0 && start < horizon) {
					upStarts.add(new double[] { start, upCrops.size() });
					upCrops.add(e.crops());
				}
			}
			upStarts.sort(Comparator.comparingDouble(a -> a[0]));
			for (int i = 0; i < Math.min(n, upStarts.size()); i++) {
				double[] u = upStarts.get(i);
				list.add(new EventDisplay(EventType.JACOB_CONTEST, "Jacob's Contest",
						false, u[0], upCrops.get((int) u[1])));
			}
		}

		// 1b) Mining-Event live aus dem Scoreboard (Dwarven Mines / Crystal Hollows).
		if (cfg.isEventEnabled(EventType.MINING_EVENT)) {
			String mining = MiningEventReader.INSTANCE.activeEvent();
			if (mining != null) {
				list.add(new EventDisplay(EventType.MINING_EVENT, mining, true,
						MiningEventReader.INSTANCE.remainingSeconds()));
			}
		}

		// 2) Live im Chat erkannte Events.
		Map<EventType, Long> live = LiveEventTracker.INSTANCE.snapshot();
		long nowMs = System.currentTimeMillis();
		for (Map.Entry<EventType, Long> e : live.entrySet()) {
			EventType type = e.getKey();
			if (!cfg.isEventEnabled(type)) {
				continue;
			}
			double remainingSeconds = (e.getValue() - nowMs) / 1000.0;
			list.add(new EventDisplay(type, type.displayName, true, remainingSeconds));
		}

		// 3) Kalender-Events (nur wenn ein gültiges SkyBlock-Datum vorliegt):
		//    aktueller + die nächsten N Termine.
		if (cal.isValid()) {
			for (EventType type : EventType.values()) {
				if (type.isLiveOnly() || !cfg.isEventEnabled(type)) {
					continue;
				}
				int n = cfg.getUpcomingEvent(type);
				double activeRemaining = -1;
				for (EventType.Occurrence o : type.occurrences) {
					double ar = cal.realSecondsActiveRemaining(o.month(), o.day(), o.durationDays());
					if (ar >= 0) {
						activeRemaining = Math.max(activeRemaining, ar);
					}
				}
				if (activeRemaining >= 0) {
					list.add(new EventDisplay(type, type.displayName, true, activeRemaining));
				}

				List<Double> upcoming = new ArrayList<>();
				for (EventType.Occurrence o : type.occurrences) {
					upcoming.addAll(cal.upcomingStarts(o.month(), o.day(), n));
				}
				upcoming.sort(Double::compareTo);
				for (int i = 0; i < Math.min(n, upcoming.size()); i++) {
					list.add(new EventDisplay(type, type.displayName, false, upcoming.get(i)));
				}
			}
		}

		// Aktive Events zuerst, dann nach verbleibender Zeit aufsteigend.
		list.sort(Comparator
				.comparingInt((EventDisplay d) -> d.active ? 0 : 1)
				.thenComparingDouble(d -> d.secondsRemaining));

		current = list;

		// Diagnose (~alle 10 s): warum Jacob (nicht) erscheint.
		if (++diagTick >= 20) {
			diagTick = 0;
			SkyblockHook hk = SkyblockHook.INSTANCE;
			if (hk.onSkyblock) {
				long shown = list.stream().filter(d -> d.type == EventType.JACOB_CONTEST).count();
				System.out.println("[Midgard] DIAG jacob: onSB=" + hk.onSkyblock + " calValid=" + cal.isValid()
						+ " sbDate=" + hk.month + "/" + hk.day
						+ " gespeichert=" + JacobSchedule.INSTANCE.entries().size()
						+ " angezeigt=" + shown);
			}
		}
	}

	private int diagTick = 0;
}
