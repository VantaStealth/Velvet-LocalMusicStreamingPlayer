import { execFileSync } from "node:child_process";
import { createHash, createHmac } from "node:crypto";
import {
  readFileSync,
  writeFileSync,
  readdirSync,
  statSync,
  existsSync,
  openSync,
  readSync,
  closeSync,
  fstatSync
} from "node:fs";
import path from "node:path";

const owner = "VantaStealth";
const repo = "Velvet-LocalMusicStreamingPlayer";
const tag = "velvet-cloud";
const musicDir = "C:\\Users\\Umar\\Velvet Cloud";
const outManifest = path.resolve("tools/velvet-cloud-manifest.json");

const token = execFileSync("git", ["credential", "fill"], {
  input: "protocol=https\nhost=github.com\n\n",
  encoding: "utf8"
}).split(/\r?\n/).find((line) => line.startsWith("password="))?.slice(9);

if (!token) throw new Error("No GitHub credential available.");

// Optional backup mirrors. Set VELVET_MIRRORS to a JSON array of S3-compatible
// stores (Cloudflare R2, Backblaze B2, AWS S3, MinIO). Credentials stay in the
// environment and are never written to the manifest or either app.
const mirrors = (() => {
  const raw = process.env.VELVET_MIRRORS?.trim();
  if (!raw) return [];
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed)) throw new Error("VELVET_MIRRORS must be a JSON array.");
  return parsed.map((entry, index) => {
    const required = ["endpoint", "bucket", "region", "accessKey", "secretKey", "publicUrl"];
    const missing = required.filter((key) => !entry?.[key]);
    if (missing.length) throw new Error(`Mirror ${index} is missing ${missing.join(", ")}.`);
    return {
      endpoint: entry.endpoint.replace(/\/+$/, ""),
      bucket: entry.bucket,
      region: entry.region,
      accessKey: entry.accessKey,
      secretKey: entry.secretKey,
      publicUrl: entry.publicUrl.replace(/\/+$/, ""),
      prefix: (entry.prefix || "").replace(/^\/+|\/+$/g, "")
    };
  });
})();

const sha256 = (value) => createHash("sha256").update(value).digest("hex");
const hmac = (key, value) => createHmac("sha256", key).update(value).digest();
const rfc3986 = (value) =>
  encodeURIComponent(value).replace(/[!'()*]/g, (char) =>
    `%${char.charCodeAt(0).toString(16).toUpperCase()}`
  );
const encodeKey = (key) => key.split("/").map(rfc3986).join("/");

function mirrorObject(mirror, name) {
  const endpoint = new URL(mirror.endpoint);
  const key = mirror.prefix ? `${mirror.prefix}/${name}` : name;
  const canonicalUri = `${endpoint.pathname.replace(/\/+$/, "")}/${encodeURIComponent(mirror.bucket)}/${encodeKey(key)}`;
  return { endpoint, key, canonicalUri, url: `${endpoint.origin}${canonicalUri}` };
}

function publicUrl(mirror, name) {
  const key = mirror.prefix ? `${mirror.prefix}/${name}` : name;
  return `${mirror.publicUrl}/${encodeKey(key)}`;
}

async function mirrorHas(mirror, file) {
  try {
    const response = await fetch(publicUrl(mirror, file.name), { method: "HEAD" });
    return response.ok && Number(response.headers.get("content-length")) === file.size;
  } catch {
    return false;
  }
}

async function mirrorPut(mirror, file) {
  const data = readFileSync(file.full);
  const { endpoint, canonicalUri, url } = mirrorObject(mirror, file.name);
  const amzDate = new Date().toISOString().replace(/[:-]|\.\d{3}/g, "");
  const dateStamp = amzDate.slice(0, 8);
  const payloadHash = sha256(data);
  const canonicalHeaders =
    `host:${endpoint.host}\n` +
    `x-amz-content-sha256:${payloadHash}\n` +
    `x-amz-date:${amzDate}\n`;
  const signedHeaders = "host;x-amz-content-sha256;x-amz-date";
  const canonicalRequest =
    `PUT\n${canonicalUri}\n\n${canonicalHeaders}\n${signedHeaders}\n${payloadHash}`;
  const scope = `${dateStamp}/${mirror.region}/s3/aws4_request`;
  const stringToSign = `AWS4-HMAC-SHA256\n${amzDate}\n${scope}\n${sha256(canonicalRequest)}`;
  const signingKey = hmac(
    hmac(hmac(hmac(`AWS4${mirror.secretKey}`, dateStamp), mirror.region), "s3"),
    "aws4_request"
  );
  const signature = createHmac("sha256", signingKey).update(stringToSign).digest("hex");

  const response = await fetch(url, {
    method: "PUT",
    headers: {
      Authorization:
        `AWS4-HMAC-SHA256 Credential=${mirror.accessKey}/${scope}, ` +
        `SignedHeaders=${signedHeaders}, Signature=${signature}`,
      "x-amz-content-sha256": payloadHash,
      "x-amz-date": amzDate,
      "Content-Type": "application/octet-stream",
      "Content-Length": String(file.size)
    },
    body: data
  });
  if (!response.ok) {
    throw new Error(`${response.status} ${(await response.text()).slice(0, 180)}`);
  }
}

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
const previousLyrics = new Map();
const oldManifestAsset = (release.assets || []).find((asset) => asset.name === "manifest.json");
if (oldManifestAsset) {
  try {
    const response = await fetch(oldManifestAsset.browser_download_url);
    if (response.ok) {
      const old = await response.json();
      for (const track of old.tracks || []) {
        const assetName = decodeURIComponent(track.url.split("/").pop());
        if (track.name && assetName) previousNames.set(track.name, assetName);
        if (track.name && track.lyrics) {
          const lyricAsset = decodeURIComponent(track.lyrics.split("/").pop());
          if (lyricAsset) previousLyrics.set(track.name, lyricAsset);
        }
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

// The apps cannot rely on a device's tag reader for cloud tracks: on some
// phones the SAF URI yields no artist at all. Publish the real tags in the
// manifest so every client knows the title and artist before it even plays.
function syncSafeSize(buffer, offset) {
  return (
    ((buffer[offset] & 0x7f) << 21) |
    ((buffer[offset + 1] & 0x7f) << 14) |
    ((buffer[offset + 2] & 0x7f) << 7) |
    (buffer[offset + 3] & 0x7f)
  );
}

function decodeId3Text(data, encoding) {
  let text;
  if (encoding === 1 || encoding === 2) {
    let body = data;
    if (body[0] === 0xfe && body[1] === 0xff) {
      body = Buffer.from(body.subarray(2));
      if (body.length % 2) body = body.subarray(0, body.length - 1);
      body.swap16();
      text = body.toString("utf16le");
    } else {
      if (body[0] === 0xff && body[1] === 0xfe) body = body.subarray(2);
      if (body.length % 2) body = body.subarray(0, body.length - 1);
      text = body.toString("utf16le");
    }
  } else if (encoding === 3) {
    text = data.toString("utf8");
  } else {
    text = data.toString("latin1");
  }
  return text.replace(/^\uFEFF/, "").replace(/\u0000[\s\S]*$/, "").trim();
}

function readId3Tags(full) {
  const tags = {};
  let fd;
  try {
    fd = openSync(full, "r");
    const head = Buffer.alloc(10);
    readSync(fd, head, 0, 10, 0);
    if (head.toString("latin1", 0, 3) === "ID3") {
      const version = head[3];
      const size = syncSafeSize(head, 6);
      const body = Buffer.alloc(Math.min(size, 4 * 1024 * 1024));
      readSync(fd, body, 0, body.length, 10);
      let offset = 0;
      if ((head[5] & 0x40) !== 0) {
        offset += version === 4 ? syncSafeSize(body, 0) : 4 + body.readUInt32BE(0);
      }
      while (offset + 10 <= body.length) {
        const id = body.toString("latin1", offset, offset + 4);
        if (!/^[A-Z0-9]{4}$/.test(id)) break;
        const frameSize =
          version === 4 ? syncSafeSize(body, offset + 4) : body.readUInt32BE(offset + 4);
        const start = offset + 10;
        if (frameSize <= 0 || start + frameSize > body.length) break;
        if (id === "TIT2" || id === "TPE1" || id === "TALB") {
          const text = decodeId3Text(body.subarray(start + 1, start + frameSize), body[start]);
          if (text) {
            tags[id === "TIT2" ? "title" : id === "TPE1" ? "artist" : "album"] = text;
          }
        }
        offset = start + frameSize;
      }
    }
    const stat = fstatSync(fd);
    if (stat.size > 128) {
      const tail = Buffer.alloc(128);
      readSync(fd, tail, 0, 128, stat.size - 128);
      if (tail.toString("latin1", 0, 3) === "TAG") {
        const field = (start, length) =>
          tail.toString("latin1", start, start + length).replace(/\u0000[\s\S]*$/, "").trim();
        if (!tags.title) tags.title = field(3, 30);
        if (!tags.artist) tags.artist = field(33, 30);
        if (!tags.album) tags.album = field(63, 30);
      }
    }
  } catch {
    // A missing or malformed tag should not stop the library from publishing.
  } finally {
    if (fd !== undefined) closeSync(fd);
  }
  return tags;
}

const files = readdirSync(musicDir)
  .filter((name) => /\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|wma)$/i.test(name))
  .sort((a, b) => a.localeCompare(b))
  .map((name) => {
    const full = path.join(musicDir, name);
    const stat = statSync(full);

    // Sidecars next to the audio travel with it: <track>.lrc is uploaded as an
    // asset and linked from the track entry so every client gets lyrics.
    const base = name.replace(/\.[^.]+$/, "");
    let lyric = null;
    for (const extension of [".lrc", ".LRC"]) {
      const lyricPath = path.join(musicDir, base + extension);
      if (!existsSync(lyricPath)) continue;
      const lyricStat = statSync(lyricPath);
      lyric = {
        name: base + extension,
        full: lyricPath,
        size: lyricStat.size,
        modified: lyricStat.mtimeMs
      };
      break;
    }

    return {
      name,
      full,
      size: stat.size,
      modified: stat.mtimeMs,
      lyric,
      tags: readId3Tags(full)
    };
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

  const backupUrls = [];
  for (const mirror of mirrors) {
    try {
      if (await mirrorHas(mirror, file)) {
        console.log(`mirror   ${file.name} (already present)`);
      } else {
        await mirrorPut(mirror, file);
        console.log(`mirror   ${file.name} (uploaded)`);
      }
      backupUrls.push(publicUrl(mirror, file.name));
    } catch (error) {
      console.warn(`mirror   ${file.name} failed: ${error.message}`);
    }
  }

  let lyricAsset = null;
  if (file.lyric) {
    const candidates = [
      file.lyric.name,
      normalize(file.lyric.name),
      previousLyrics.get(file.name)
    ].filter(Boolean);
    lyricAsset = candidates.map((name) => assetsByName.get(name)).find(Boolean);

    if (lyricAsset) {
      claimed.add(lyricAsset.id);
      removedAssets.delete(lyricAsset.id);
      const stale =
        lyricAsset.size !== file.lyric.size ||
        new Date(lyricAsset.updated_at).getTime() < file.lyric.modified;
      if (stale) {
        await deleteAsset(lyricAsset);
        lyricAsset = await upload(file.lyric);
        uploaded += 1;
        bytes += file.lyric.size;
        console.log(`lyrics   ${file.lyric.name} (replaced)`);
      } else {
        console.log(`lyrics   ${file.lyric.name} (present)`);
      }
    } else {
      lyricAsset = await upload(file.lyric);
      uploaded += 1;
      bytes += file.lyric.size;
      console.log(`lyrics   ${file.lyric.name} (uploaded)`);
    }
  }

  const track = { name: file.name, size: asset.size, url: asset.browser_download_url };
  if (file.tags.title) track.title = file.tags.title;
  if (file.tags.artist) track.artist = file.tags.artist;
  if (file.tags.album) track.album = file.tags.album;
  if (backupUrls.length) track.mirrors = backupUrls;
  if (lyricAsset) track.lyrics = lyricAsset.browser_download_url;
  tracks.push(track);
}

for (const asset of removedAssets.values()) {
  await deleteAsset(asset);
}

const manifest = {
  name: "Velvet Cloud",
  version: 5,
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
