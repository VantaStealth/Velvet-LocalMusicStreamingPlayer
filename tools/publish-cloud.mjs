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
const deleteBase = `https://api.github.com/repos/${owner}/${repo}/releases/assets`;

// GitHub rewrites asset names (spaces and punctuation change). The previous
// manifest remembers the rewritten name for each source file, which also lets
// us replace an asset whose bytes changed and delete tracks that were removed.
const previousNames = new Map();
const oldManifestAsset = (release.assets || []).find((asset) => asset.name === "manifest.json");
if (oldManifestAsset) {
  try {
    const response = await fetch(oldManifestAsset.browser_download_url);
    if (response.ok) {
      const old = await response.json();
      for (const track of old.tracks || []) {
        const assetName = decodeURIComponent(track.url.split("/").pop());
        if (track.name && assetName) previousNames.set(track.name, assetName);
      }
    }
  } catch {
    console.log("could not read the previous manifest; matching by name only");
  }
}

const normalize = (value) =>
  value
    .replace(/[\[\]()｜|'’]/g, "")
    .replaceAll(" ", ".")
    .replace(/\.{2,}/g, ".");

const files = readdirSync(musicDir)
  .filter((name) => /\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|wma)$/i.test(name))
  .sort((a, b) => a.localeCompare(b))
  .map((name) => {
    const full = path.join(musicDir, name);
    const stat = statSync(full);
    return { name, full, size: stat.size, modified: stat.mtimeMs };
  });

const assetsByName = new Map();
for (const asset of release.assets || []) {
  assetsByName.set(asset.name, asset);
  assetsByName.set(normalize(asset.name), asset);
}

const claimed = new Set();
const removedAssets = new Map();

for (const file of files) {
  const candidates = [
    file.name,
    normalize(file.name),
    previousNames.get(file.name)
  ].filter(Boolean);
  file.asset = candidates.map((name) => assetsByName.get(name)).find(Boolean);
  if (!file.asset) continue;

  claimed.add(file.asset.id);
  file.stale =
    file.asset.size !== file.size ||
    new Date(file.asset.updated_at).getTime() < file.modified;
}

// Anything left over is a track that no longer exists in the library.
for (const asset of release.assets || []) {
  if (asset.name === "manifest.json") continue;
  if (!claimed.has(asset.id)) removedAssets.set(asset.id, asset);
}

async function deleteAsset(asset) {
  const removed = await api(`${deleteBase}/${asset.id}`, { method: "DELETE" });
  if (removed.status >= 300 && removed.status !== 404) {
    throw new Error(`Could not remove ${asset.name}: ${removed.status} ${JSON.stringify(removed.body)}`);
  }
  console.log(`removed  ${asset.name}`);
}

async function upload(file, attempt = 1) {
  const data = readFileSync(file.full);
  const response = await fetch(`${uploadBase}?name=${encodeURIComponent(file.name)}`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/octet-stream", "Content-Length": String(file.size) },
    body: data
  });
  if (response.ok) return response.json();

  if (response.status === 422 && attempt < 4) {
    await new Promise((resolve) => setTimeout(resolve, 1200 * attempt));
    return upload(file, attempt + 1);
  }
  const detail = await response.text();
  throw new Error(`Upload failed for ${file.name}: ${response.status} ${detail}`);
}

let uploaded = 0;
let bytes = 0;
const tracks = [];

// Replace one file at a time so the cloud never loses more than a single
// track while its new bytes are in flight.
for (const file of files) {
  let asset = file.asset;
  if (!asset) {
    asset = await upload(file);
    uploaded += 1;
    bytes += file.size;
    console.log(`uploaded ${file.name} (${(file.size / 1048576).toFixed(1)} MB)`);
  } else if (file.stale) {
    await deleteAsset(asset);
    asset = await upload(file);
    uploaded += 1;
    bytes += file.size;
    console.log(`replaced ${file.name} (${(file.size / 1048576).toFixed(1)} MB)`);
  } else {
    console.log(`present  ${file.name}`);
  }
  tracks.push({ name: file.name, size: asset.size, url: asset.browser_download_url });
}

for (const asset of removedAssets.values()) {
  await deleteAsset(asset);
}

const manifest = {
  name: "Velvet Cloud",
  version: 2,
  updated: new Date().toISOString().slice(0, 10),
  tracks
};
writeFileSync(outManifest, JSON.stringify(manifest, null, 2));

const oldManifest = (release.assets || []).find((asset) => asset.name === "manifest.json");
if (oldManifest) {
  const removed = await api(`${deleteBase}/${oldManifest.id}`, { method: "DELETE" });
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
