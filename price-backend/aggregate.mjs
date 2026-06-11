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
// Die SkyBlock-Item-ID steckt im NBT (item_bytes, base64+gzip).
async function skyblockId(itemBytes) {
	try {
		const { parsed } = await nbt.parse(Buffer.from(itemBytes, 'base64'));
		const items = parsed.value?.i?.value?.value;
		const tag = items?.[0]?.tag?.value;
		return tag?.ExtraAttributes?.value?.id?.value || null;
	} catch {
		return null;
	}
}

async function auctions() {
	const first = await getJson('https://api.hypixel.net/v2/skyblock/auctions?page=0');
	const pages = first.totalPages || 1;
	const byId = {}; // SkyBlock-ID -> Liste der BIN-Preise

	const handlePage = async (aucs) => {
		await Promise.all((aucs || []).map(async (a) => {
			if (!a.bin) return; // nur Sofortkauf (BIN) als Preisreferenz
			const id = await skyblockId(a.item_bytes);
			if (!id) return;
			(byId[id] ||= []).push(a.starting_bid);
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

	const out = {};
	for (const [id, arr] of Object.entries(byId)) {
		arr.sort((a, b) => a - b);
		const sum = arr.reduce((s, x) => s + x, 0);
		out[id] = {
			min: arr[0],
			max: arr[arr.length - 1],
			avg: Math.round(sum / arr.length),
			lowestBin: arr[0],
			count: arr.length
		};
	}
	return out;
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
const out = {
	updated: Math.floor(Date.now() / 1000),
	source: 'Hypixel API + elitebot.dev',
	bazaar: await bazaar(),
	auctions: await auctions(),
	jacob: await jacob()
};

await mkdir('public', { recursive: true });
await writeFile('public/prices.json', JSON.stringify(out));
console.log(
	`OK: ${Object.keys(out.bazaar).length} Bazaar, ` +
	`${Object.keys(out.auctions).length} AH-Items, ` +
	`${Object.keys(out.jacob).length} Jacob-Contests`
);
