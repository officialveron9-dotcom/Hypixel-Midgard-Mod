package com.midgard.events.event;

import java.util.List;

/** Ein fertig berechneter Eintrag, wie er im HUD angezeigt wird. */
public class EventDisplay {

	public final EventType type;
	public final String label;
	/** true = läuft gerade, false = kommt erst noch. */
	public final boolean active;
	/** Verbleibende Sekunden bis Ende (aktiv) bzw. bis Start (kommend). */
	public final double secondsRemaining;
	/** Nur für Jacob's Contest gesetzt: die drei Crop-Namen. Sonst null. */
	public final List<String> crops;

	public EventDisplay(EventType type, String label, boolean active, double secondsRemaining) {
		this(type, label, active, secondsRemaining, null);
	}

	public EventDisplay(EventType type, String label, boolean active, double secondsRemaining, List<String> crops) {
		this.type = type;
		this.label = label;
		this.active = active;
		this.secondsRemaining = secondsRemaining;
		this.crops = crops;
	}
}
