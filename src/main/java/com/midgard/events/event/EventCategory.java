package com.midgard.events.event;

/** Kategorien, nach denen Events gruppiert und (de)aktiviert werden können. */
public enum EventCategory {
	CALENDAR("Kalender-Events"),
	JACOB("Jacob's Contests"),
	MINING("Mining-Events"),
	FISHING("Fishing-Events"),
	OTHER("Sonstige Events");

	public final String displayName;

	EventCategory(String displayName) {
		this.displayName = displayName;
	}
}
