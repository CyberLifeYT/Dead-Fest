/**
 * Stamps the built executables with the Dead-Fest icon and version information.
 *
 * Two wrinkles, both worth explaining:
 *
 * 1. electron-builder's own resource editor (rcedit) normally does this, but it ships in a
 *    package whose extraction needs symlink privileges on Windows â€” a privilege a normal user
 *    does not have, so the build cannot download it here. The tool itself is fine, so this
 *    script finds the already-downloaded copy in electron-builder's cache and runs it directly.
 *
 * 2. rcedit rewrites the executable and DROPS any data appended after the PE sections. For a
 *    normal .exe that is harmless, but electron-builder's portable build is a 7-Zip self-extractor
 *    whose payload lives in exactly that appended region â€” stamping it naively turns a 74 MB
 *    application into an 82 KB stub that does nothing. So the overlay is measured out of the
 *    original file and put back afterwards, which leaves a self-extractor that is both branded
 *    and whole.
 *
 *   npm run dist
 */
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const DIST = path.join(ROOT, 'dist');
const ICON = path.join(ROOT, 'assets', 'icon.ico');
const CACHE = path.join(process.env.LOCALAPPDATA || '', 'electron-builder', 'Cache', 'winCodeSign');

function findRcedit() {
  if (!fs.existsSync(CACHE)) return null;
  for (const dir of fs.readdirSync(CACHE)) {
    const candidate = path.join(CACHE, dir, 'rcedit-x64.exe');
    if (fs.existsSync(candidate)) return candidate;
  }
  return null;
}

/** Byte offset where the PE sections end and any appended overlay begins. */
function overlayStart(buffer) {
  if (buffer.length < 0x40) return 0;
  if (buffer.readUInt16LE(0) !== 0x5a4d) return 0;         // "MZ"
  const peOffset = buffer.readUInt32LE(0x3c);
  if (buffer.readUInt32LE(peOffset) !== 0x00004550) return 0; // "PE\0\0"
  const sectionCount = buffer.readUInt16LE(peOffset + 6);
  const optionalSize = buffer.readUInt16LE(peOffset + 20);
  const sectionTable = peOffset + 24 + optionalSize;
  let end = 0;
  for (let i = 0; i < sectionCount; i++) {
    const at = sectionTable + i * 40;
    const rawSize = buffer.readUInt32LE(at + 16);
    const rawPointer = buffer.readUInt32LE(at + 20);
    end = Math.max(end, rawPointer + rawSize);
  }
  return end;
}

function stamp(target) {
  if (!fs.existsSync(target)) return false;
  const rcedit = findRcedit();
  if (!rcedit) {
    console.warn('  rcedit not found in the electron-builder cache; skipping ' + path.basename(target));
    return false;
  }

  const original = fs.readFileSync(target);
  const overlayAt = overlayStart(original);
  const overlay = original.subarray(overlayAt);
  const hasOverlay = overlay.length > 0;

  // Stamp a copy: rcedit rewrites the file in place.
  const work = path.join(DIST, path.basename(target) + '.stamp.tmp');
  fs.writeFileSync(work, original);
  try {
    execFileSync(rcedit, [
      work,
      '--set-icon', ICON,
      '--set-version-string', 'FileDescription', 'Dead-Fest Survival Terminal',
      '--set-version-string', 'ProductName', 'Dead-Fest',
      '--set-version-string', 'CompanyName', 'CyberLifeYT',
      '--set-version-string', 'InternalName', 'Dead-Fest',
      '--set-file-version', '2.1.0.0',
      '--set-product-version', '2.1.0.0'
    ], { stdio: 'pipe' });
  } catch (e) {
    console.warn('  rcedit failed on ' + path.basename(target) + ': ' + (e.message || e));
    fs.unlinkSync(work);
    return false;
  }

  const stamped = fs.readFileSync(work);
  fs.unlinkSync(work);

  if (!hasOverlay) {
    fs.writeFileSync(target, stamped);
    console.log('  stamped ' + path.basename(target));
    return true;
  }

  // Put the appended payload back, or the self-extractor has nothing to extract.
  fs.writeFileSync(target, Buffer.concat([stamped, overlay]));
  const after = fs.statSync(target).size;
  if (after < original.length * 0.95) {
    console.warn('  ' + path.basename(target) + ' shrank unexpectedly (' + after + ' bytes) â€” restoring the original');
    fs.writeFileSync(target, original);
    return false;
  }
  console.log('  stamped ' + path.basename(target) + ' (overlay of ' + overlay.length + ' bytes preserved)');
  return true;
}

const targets = fs.existsSync(DIST)
  ? fs.readdirSync(DIST)
      .filter((f) => f.endsWith('.exe'))
      .map((f) => path.join(DIST, f))
      .concat([path.join(DIST, 'win-unpacked', 'Dead-Fest.exe')])
  : [];

if (!fs.existsSync(ICON)) {
  console.error('assets/icon.ico is missing â€” run `npm run icon` first.');
  process.exit(1);
}

let stamped = 0;
for (const target of targets) {
  if (stamp(target)) stamped++;
}
console.log('stamped ' + stamped + ' executable(s)');

