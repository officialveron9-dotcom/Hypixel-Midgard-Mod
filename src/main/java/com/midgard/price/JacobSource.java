package com.midgard.price;

import java.util.List;
import java.util.Map;

/** Eine Quelle für den Jacob-Zeitplan (Backend-prices.json ODER direkt elitebot). */
public interface JacobSource {

	boolean hasJacob();

	/** Laufender Contest (startSec -> crops) oder null. */
	Map.Entry<Long, List<String>> jacobActive(long nowSec);

	/** Die nächsten {@code count} Contests (startSec -> crops). */
	List<Map.Entry<Long, List<String>>> jacobUpcoming(long nowSec, int count);
}
