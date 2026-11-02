import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";

const owner = "VantaStealth";
const repo = "Velvet-LocalMusicStreaming";
const tag = "velvet-cloud";
const musicDir = "C:\\Users\\Umar\\Velvet Cloud";
const outManifest = path.resolve("tools/velvet-cloud-manifest.json");

const token = execFileSync("git", ["credential", "fill"], {
  input: "protocol=https\nhost=github.com\n\n",
  encoding: "utf8"
}).split(/\r?\n/).find((line) => line.startsWith("password="))?.slice(9);

if (!token) throw new Error("No GitHub credential available.");

const headers = {
  Authorization: `Bearer ${token}`,
  Accept: "application/vnd.github+json",
  "X-GitHub-Api-Version": "2022-11-28",
  "User-Agent": "Velvet-Cloud-Publisher"
};

async function api(url, options = {}) {
  const response = await fetch(url, { ...options, headers: { ...headers, ...(options.headers || {}) } });
  const text = await response.text();
  let body;
  try { body = text ? JSON.parse(text) : {}; } catch { body = { raw: text }; }
  return { status: response.status, body };
}

let release;
const existing = await api(`https://api.github.com/repos/${owner}/${repo}/releases/tags/${tag}`);
if (existing.status === 200) {
  release = existing.body;
} else {
  const created = await api(`https://api.github.com/repos/${owner}/${repo}/releases`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      tag_name: tag,
      target_commitish: "main",
      name: "Velvet Cloud",
      body: "The Velvet Cloud library: every bundled track, streamed by Velvet Desktop and Velvet Mobile. Download from either app or grab the files directly.",
      draft: false,
      prerelease: false
    })
  });
  if (created.status >= 300) throw new Error(`Release create failed: ${created.status} ${JSON.stringify(created.body)}`);
  release = created.body;
}

const uploadBase = `https://uploads.github.com/repos/${owner}/${repo}/releases/${release.id}/assets`;
const files = readdirSync(musicDir)
  .filter((name) => /\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|wma)$/i.test(name))
  .sort((a, b) => a.localeCompare(b));

// GitHub rewrites asset names (spaces to dots, punctuation stripped); match by name then size.
const assets = new Map((release.assets || []).map((asset) => [asset.name, asset]));
const bySize = new Map();
for (const asset of release.assets || []) {
  if (!bySize.has(asset.size)) bySize.set(asset.size, []);
  bySize.get(asset.size).push(asset);
}
const normalize = (value) =>
  value.replace(/[[\]｜]/g, "").replaceAll(" ", ".");
const tracks = [];
let uploaded = 0;
let bytes = 0;

for (const name of files) {
  const full = path.join(musicDir, name);
  const size = statSync(full).size;
  let asset = assets.get(name) ?? assets.get(normalize(name));
  if (!asset) {
    const bySameSize = bySize.get(size) || [];
    asset = bySameSize.find((candidate) =>
      candidate.name.startsWith(name.slice(0, 4))) || bySameSize[0];
  }
  if (!asset) {
    const data = readFileSync(full);
    const response = await fetch(`${uploadBase}?name=${encodeURIComponent(name)}`, {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/octet-stream", "Content-Length": String(size) },
      body: data
    });
    if (!response.ok) {
      const detail = await response.text();
      throw new Error(`Upload failed for ${name}: ${response.status} ${detail}`);
    }
    asset = await response.json();
    uploaded += 1;
    bytes += size;
    console.log(`uploaded ${name} (${(size / 1048576).toFixed(1)} MB)`);
  } else {
    console.log(`present  ${name}`);
  }
  tracks.push({ name, size: asset.size, url: asset.browser_download_url });
}

const manifest = {
  name: "Velvet Cloud",
  version: 1,
  updated: new Date().toISOString().slice(0, 10),
  tracks
};
writeFileSync(outManifest, JSON.stringify(manifest, null, 2));

const oldManifest = (release.assets || []).find((asset) => asset.name === "manifest.json");
if (oldManifest) {
  const removed = await api(`https://api.github.com/repos/${owner}/${repo}/releases/assets/${oldManifest.id}`, { method: "DELETE" });
  if (removed.status >= 300 && removed.status !== 404) {
    throw new Error(`Could not replace manifest: ${removed.status}`);
  }
}
const manifestData = Buffer.from(JSON.stringify(manifest));
const manifestResponse = await fetch(`${uploadBase}?name=manifest.json`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json", "Content-Length": String(manifestData.length) },
  body: manifestData
});
if (!manifestResponse.ok) throw new Error(`Manifest upload failed: ${manifestResponse.status}`);

console.log(`\nVelvet Cloud ready: ${tracks.length} tracks, ${(bytes / 1048576).toFixed(1)} MB uploaded this run`);
console.log(`Manifest: https://github.com/${owner}/${repo}/releases/download/${tag}/manifest.json`);
