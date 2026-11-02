import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";

const musicDir = process.argv[2] || "C:\\Users\\Umar\\Velvet Cloud";
const metadataScript = path.resolve("tools/lyrics-metadata.ps1");
const userAgent =
  "Velvet/2.0.1 (https://github.com/VantaStealth/Velvet-LocalMusicStreamingPlayer)";

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const raw = execFileSync(
  "powershell",
  ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", metadataScript, "-Folder", musicDir],
  { encoding: "utf8", maxBuffer: 8 * 1024 * 1024 }
);
const tracks = JSON.parse(raw.trim());

/** Exactly-even timestamps with a fixed 8 s start are the old fabricated format. */
function timestamps(body) {
  const times = [];
  for (const line of String(body).split(/\r?\n/)) {
    const match = line.match(/^\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]/);
    if (!match) continue;
    times.push(
      Number(match[1]) * 60000 +
        Number(match[2]) * 1000 +
        Number((match[3] || "0").padEnd(3, "0").slice(0, 3))
    );
  }
  return times.sort((a, b) => a - b);
}

function looksFabricated(body) {
  const times = timestamps(body);
  if (times.length < 3) return false;
  const steps = [];
  for (let i = 1; i < times.length; i += 1) {
    const step = times[i] - times[i - 1];
    if (step > 0) steps.push(step);
  }
  if (steps.length === 0) return false;
  const spread = Math.max(...steps) - Math.min(...steps);
  return spread <= 2 && Math.abs(times[0] - 8000) <= 2;
}

/**
 * A lyric file written for another cut (34 s clip, shortened edit or slowed
 * remix) cannot sync to this audio. Require its last line to land inside the
 * track and, for long songs, not stop halfway through.
 */
function fitsDuration(body, track) {
  const times = timestamps(body);
  if (times.length === 0) return false;
  const duration = track.durationMs;
  if (!duration || duration < 30000) return true;
  const last = times[times.length - 1];
  if (last > duration + 4000) return false;
  return duration < 90000 || last >= duration / 2;
}

function normalize(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/\([^)]*\)|\[[^\]]*\]/g, " ")
    .replace(/\b(feat|ft|featuring|with)\b\.?.*$/, " ")
    .replace(/\b(remaster(ed)?|radio edit|explicit|deluxe|bonus track|version)\b/g, " ")
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function similarity(left, right) {
  if (!left || !right) return 0;
  if (left === right) return 1;
  if (left.includes(right) || right.includes(left)) return 0.9;
  const a = new Set(left.split(" "));
  const b = new Set(right.split(" "));
  let shared = 0;
  for (const token of a) if (b.has(token)) shared += 1;
  return shared / Math.max(a.size, b.size);
}

function candidateScore(item, track) {
  const title = similarity(normalize(track.title), normalize(item.trackName));
  const artist = similarity(normalize(track.artist), normalize(item.artistName));
  if (title < 0.7 || artist < 0.5) return null;
  let score = title * 0.5 + artist * 0.5;
  if (track.durationMs > 0 && typeof item.duration === "number" && item.duration > 0) {
    const delta = Math.abs(track.durationMs / 1000 - item.duration);
    if (delta > 5) return null;
    score += delta <= 2 ? 0.22 : 0.1;
  }
  return score;
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

async function fetchLyrics(track) {
  const params = new URLSearchParams({
    track_name: track.title,
    artist_name: track.artist
  });
  if (track.album) params.set("album_name", track.album);
  if (track.durationMs > 0) params.set("duration", String(Math.round(track.durationMs / 1000)));

  const direct = await requestJson(`https://lrclib.net/api/get?${params}`);
  if (
    direct?.syncedLyrics &&
    candidateScore(direct, track) !== null &&
    fitsDuration(direct.syncedLyrics, track)
  ) {
    return { body: direct.syncedLyrics, kind: "synced" };
  }

  const search = new URLSearchParams({ q: `${track.artist} ${track.title}` });
  const results = await requestJson(`https://lrclib.net/api/search?${search}`);
  if (!Array.isArray(results)) return null;

  let best = null;
  let bestScore = 0;
  for (const item of results) {
    if (!item.syncedLyrics || !fitsDuration(item.syncedLyrics, track)) continue;
    const score = candidateScore(item, track);
    if (score !== null && score > bestScore) {
      bestScore = score;
      best = item.syncedLyrics;
    }
  }
  return bestScore >= 0.68 ? { body: best, kind: "synced" } : null;
}

let synced = 0;
let replaced = 0;
let removed = 0;
let existing = 0;
let missing = 0;

for (const track of tracks) {
  const base = track.name.replace(/\.[^.]+$/, "");
  const sidecar = path.join(musicDir, `${base}.lrc`);
  if (existsSync(sidecar)) {
    const body = readFileSync(sidecar, "utf8");
    const fabricated = looksFabricated(body);
    if (!fabricated && fitsDuration(body, track)) {
      existing += 1;
      console.log(`present  ${base}.lrc`);
      continue;
    }
    const reason = fabricated
      ? "fabricated timings"
      : "lyrics do not match the track length";
    const fixed = track.title && track.artist ? await fetchLyrics(track) : null;
    if (fixed?.body) {
      writeFileSync(sidecar, fixed.body, "utf8");
      replaced += 1;
      console.log(`fixed    ${base}.lrc (${reason})`);
    } else {
      rmSync(sidecar);
      removed += 1;
      console.log(`removed  ${base}.lrc (${reason}, no synced match)`);
    }
    await wait(240);
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
    synced += 1;
    console.log(`synced   ${base}.lrc`);
  }
  await wait(240);
}

console.log(
  `\n${synced} synced, ${replaced} repaired, ${removed} fabricated removed, ${existing} already present, ${missing} without a match`
);
