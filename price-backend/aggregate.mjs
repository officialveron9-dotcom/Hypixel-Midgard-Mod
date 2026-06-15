// Midgard Preis-Backend
// Holt Hypixel Bazaar + Auktionshaus (kein API-Key nötig) und den Jacob-Zeitplan
// (von elitebot.dev, 1x pro Lauf) und schreibt eine kompakte public/prices.json.
// Läuft als GitHub Action (alle ~5 Min) und veröffentlicht die JSON auf GitHub Pages.

import nbt from 'prismarine-nbt';
import { writeFile, mkdir } from 'node:fs/promises';

const UA = { 'User-Agent': 'Midgard-Price-Backend (Fabric Mod)' };

async function getJson(url) {
	const r = await fetch(url, { headers: UA });
	if (!r.ok) throw new Error(`${url} -> HTTP ${r.status}`);
	return r.json();
}

function round(n) {
	return n == null ? null : Math.round(n * 10) / 10;
}

// ---- Bazaar -------------------------------------------------------------
async function bazaar() {
	const d = await getJson('https://api.hypixel.net/v2/skyblock/bazaar');
	const out = {};
	for (const [id, p] of Object.entries(d.products || {})) {
		const q = p.quick_status || {};
		out[id] = { buy: round(q.buyPrice), sell: round(q.sellPrice) };
	}
	return out;
}

// ---- Auktionshaus -------------------------------------------------------
// Die SkyBlock-Item-ID + Sterne stecken im NBT (item_bytes, base64+gzip).
async function parseItem(itemBytes) {
	try {
		const { parsed } = await nbt.parse(Buffer.from(itemBytes, 'base64'));
		const ea = parsed.value?.i?.value?.value?.[0]?.tag?.value?.ExtraAttributes?.value;
		const id = ea?.id?.value || null;
		let stars = ea?.upgrade_level?.value;
		if (stars == null) stars = ea?.dungeon_item_level?.value;
		stars = Math.max(0, Math.min(10, (stars | 0)));
		return { id, stars };
	} catch {
		return { id: null, stars: 0 };
	}
}

// Liefert BEIDES in einem Durchgang: Preis-Statistik je Item (für Tooltips) UND
// den kompakten Voll-Snapshot ALLER Live-Auktionen (für den AH-Browser).
// Kurze Keys halten den Snapshot klein: u=uuid i=id n=name t=tier c=category
// p=preis b=bin e=endzeit d=gebote s=sterne.
async function auctionsAndSnapshot() {
	const first = await getJson('https://api.hypixel.net/v2/skyblock/auctions?page=0');
	const pages = first.totalPages || 1;
	const byId = {};      // SkyBlock-ID -> Liste der BIN-Preise
	const snapshot = [];  // alle Auktionen kompakt

	const handlePage = async (aucs) => {
		await Promise.all((aucs || []).map(async (a) => {
			const { id, stars } = await parseItem(a.item_bytes);
			snapshot.push({
				u: a.uuid, i: id || '', n: a.item_name || '', t: a.tier || '',
				c: a.category || '', p: a.starting_bid, b: a.bin ? 1 : 0,
				e: a.end, d: Array.isArray(a.bids) ? a.bids.length : 0, s: stars
			});
			if (a.bin && id) (byId[id] ||= []).push(a.starting_bid);
		}));
	};

	await handlePage(first.auctions);
	for (let pg = 1; pg < pages; pg++) {
		try {
			const d = await getJson('https://api.hypixel.net/v2/skyblock/auctions?page=' + pg);
			await handlePage(d.auctions);
		} catch (e) {
			console.error('AH-Seite ' + pg + ' übersprungen:', e.message);
		}
	}

	const stats = {};
	for (const [id, arr] of Object.entries(byId)) {
		arr.sort((a, b) => a - b);
		const sum = arr.reduce((s, x) => s + x, 0);
		stats[id] = {
			min: arr[0], max: arr[arr.length - 1],
			avg: Math.round(sum / arr.length), lowestBin: arr[0], count: arr.length
		};
	}
	return { auctions: stats, snapshot };
}

// ---- Jacob-Zeitplan (von elitebot.dev, 1x pro Lauf) ---------------------
async function jacob() {
	try {
		const d = await getJson('https://api.elitebot.dev/contests/at/now');
		return d.contests || {}; // { unixSec: [crop1, crop2, crop3] }
	} catch (e) {
		console.error('Jacob (elitebot) übersprungen:', e.message);
		return {};
	}
}

// ---- Zusammenbauen + schreiben -----------------------------------------
const updated = Math.floor(Date.now() / 1000);
const [bz, ah, jc] = await Promise.all([bazaar(), auctionsAndSnapshot(), jacob()]);

const prices = { updated, source: 'Hypixel API + elitebot.dev', bazaar: bz, auctions: ah.auctions, jacob: jc };
const snapshot = { updated, count: ah.snapshot.length, auctions: ah.snapshot };

await mkdir('public', { recursive: true });
await writeFile('public/prices.json', JSON.stringify(prices));
await writeFile('public/snapshot.json', JSON.stringify(snapshot));
console.log(
	`OK: ${Object.keys(prices.bazaar).length} Bazaar, ` +
	`${Object.keys(prices.auctions).length} AH-Items, ` +
	`${snapshot.count} Auktionen im Snapshot, ` +
	`${Object.keys(prices.jacob).length} Jacob-Contests`
);
