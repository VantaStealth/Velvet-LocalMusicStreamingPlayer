import { execFileSync } from "node:child_process";
import { existsSync, writeFileSync } from "node:fs";
import path from "node:path";

const musicDir = process.argv[2] || "C:\\Users\\Umar\\Velvet Cloud";
const metadataScript = path.resolve("tools/lyrics-metadata.ps1");
const userAgent =
  "Velvet/2.0.0 (https://github.com/VantaStealth/Velvet-LocalMusicStreamingPlayer)";

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const raw = execFileSync(
  "powershell",
  ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", metadataScript, "-Folder", musicDir],
  { encoding: "utf8", maxBuffer: 8 * 1024 * 1024 }
);
const tracks = JSON.parse(raw.trim());

function plainToPseudoLrc(plain, durationMs) {
  if (!plain) return null;
  const lines = String(plain)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(0, 260);
  if (lines.length === 0) return null;
  const total = durationMs > 0 ? durationMs : lines.length * 3200;
  const start = Math.min(8000, total * 0.05);
  const end = total * 0.94;
  const step = Math.max(900, (end - start) / lines.length);
  return lines
    .map((line, index) => {
      const ms = Math.round(start + step * index);
      const minutes = Math.floor(ms / 60000);
      const seconds = Math.floor((ms % 60000) / 1000);
      const fraction = ms % 1000;
      return `[${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}.${String(fraction).padStart(3, "0")}]${line}`;
    })
    .join("\n");
}

async function requestJson(url, attempt = 1) {
  try {
    const response = await fetch(url, {
      headers: { "User-Agent": userAgent, Accept: "application/json" }
    });
    if (response.status === 429 || response.status >= 500) {
      if (attempt < 3) {
        await wait(320 * attempt);
        return requestJson(url, attempt + 1);
      }
      return null;
    }
    if (!response.ok) return null;
    return await response.json();
  } catch {
    if (attempt < 3) {
      await wait(320 * attempt);
      return requestJson(url, attempt + 1);
    }
    return null;
  }
}

const clean = (value) => String(value || "").toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");

async function fetchLyrics(track) {
  const params = new URLSearchParams({
    track_name: track.title,
    artist_name: track.artist
  });
  if (track.album) params.set("album_name", track.album);
  if (track.durationMs > 0) params.set("duration", String(Math.round(track.durationMs / 1000)));

  const direct = await requestJson(`https://lrclib.net/api/get?${params}`);
  if (direct?.syncedLyrics) return { body: direct.syncedLyrics, kind: "synced" };
  let plain = direct?.plainLyrics || null;

  const search = new URLSearchParams({ q: `${track.artist} ${track.title}` });
  const results = await requestJson(`https://lrclib.net/api/search?${search}`);
  if (Array.isArray(results)) {
    const wantTitle = clean(track.title);
    const wantArtist = clean(track.artist.split(/[,&]|feat\.?/i)[0]);
    const scored = results
      .map((item) => {
        const title = clean(item.trackName);
        const artist = clean(item.artistName);
        let score = 0;
        if (title === wantTitle) score += 3;
        else if (title.includes(wantTitle) || wantTitle.includes(title)) score += 1;
        if (artist.includes(wantArtist) || wantArtist.includes(artist)) score += 2;
        if (
          track.durationMs > 0 &&
          typeof item.duration === "number" &&
          Math.abs(item.duration * 1000 - track.durationMs) < 6000
        ) {
          score += 2;
        }
        return { item, score };
      })
      .sort((a, b) => b.score - a.score);

    for (const { item } of scored) {
      if (item.syncedLyrics) return { body: item.syncedLyrics, kind: "synced" };
      plain ??= item.plainLyrics || null;
    }
  }

  if (plain) return { body: plainToPseudoLrc(plain, track.durationMs), kind: "plain" };

  const ovh = await requestJson(
    `https://api.lyrics.ovh/v1/${encodeURIComponent(track.artist)}/${encodeURIComponent(track.title)}`
  );
  if (ovh?.lyrics) {
    return { body: plainToPseudoLrc(ovh.lyrics, track.durationMs), kind: "plain" };
  }
  return null;
}

let synced = 0;
let plainCount = 0;
let existing = 0;
let missing = 0;

for (const track of tracks) {
  const base = track.name.replace(/\.[^.]+$/, "");
  const sidecar = path.join(musicDir, `${base}.lrc`);
  if (existsSync(sidecar)) {
    existing += 1;
    console.log(`present  ${base}.lrc`);
    continue;
  }
  if (!track.title || !track.artist) {
    missing += 1;
    console.log(`skipped  ${track.name} (no tags)`);
    continue;
  }

  const lyrics = await fetchLyrics(track);
  if (!lyrics?.body) {
    missing += 1;
    console.log(`missing  ${track.artist} — ${track.title}`);
  } else {
    writeFileSync(sidecar, lyrics.body, "utf8");
    if (lyrics.kind === "synced") synced += 1;
    else plainCount += 1;
    console.log(`${lyrics.kind === "synced" ? "synced " : "plain  "} ${base}.lrc`);
  }
  await wait(240);
}

console.log(
  `\n${synced} synced, ${plainCount} timed from plain text, ${existing} already present, ${missing} without a match`
);
