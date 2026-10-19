/**
 * Renders the Dead-Fest icon and builds a Windows .ico.
 *
 * The source of truth is assets/icon.svg (the same artwork as the Android launcher icon).
 * This script renders it at every size Windows asks for, captures each as a PNG with
 * Electron's own renderer, and packs them into a multi-size .ico.
 *
 *   npm run icon
 */
const { app, BrowserWindow } = require('electron');
const fs = require('fs');
const path = require('path');

const SIZES = [16, 32, 48, 64, 128, 256];
const ASSETS = path.join(__dirname, '..', 'assets');

/** Packs PNG buffers into an ICO. Windows accepts PNG-compressed entries on Vista and later. */
function buildIco(entries) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);      // reserved
  header.writeUInt16LE(1, 2);      // type: icon
  header.writeUInt16LE(entries.length, 4);

  const directory = Buffer.alloc(16 * entries.length);
  let offset = 6 + directory.length;
  entries.forEach((entry, i) => {
    const at = i * 16;
    directory.writeUInt8(entry.size >= 256 ? 0 : entry.size, at);        // 0 means 256
    directory.writeUInt8(entry.size >= 256 ? 0 : entry.size, at + 1);
    directory.writeUInt8(0, at + 2);                                     // palette size
    directory.writeUInt8(0, at + 3);                                     // reserved
    directory.writeUInt16LE(1, at + 4);                                  // colour planes
    directory.writeUInt16LE(32, at + 6);                                 // bits per pixel
    directory.writeUInt32LE(entry.data.length, at + 8);
    directory.writeUInt32LE(offset, at + 12);
    offset += entry.data.length;
  });

  return Buffer.concat([header, directory, ...entries.map((e) => e.data)]);
}

/**
 * Renders the icon in an offscreen window and returns it as a PNG.
 *
 * The SVG is inlined rather than fetched: a data: URL has no origin, so the page needs no
 * file access at all, and the same window can be reused for every size.
 */
async function capture(win, size, svg) {
  const html = `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>html,body{margin:0;padding:0;background:transparent;overflow:hidden}
#icon{display:block;line-height:0}</style></head><body>
<div id="icon">${svg}</div>
<script>
  const el = document.querySelector('#icon svg');
  el.setAttribute('width', ${size}); el.setAttribute('height', ${size});
  window.__ready = true;
</script></body></html>`;

  win.setContentSize(size, size);
  await win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(html));
  // One frame for the SVG to paint before the capture.
  await new Promise((r) => setTimeout(r, 120));
  const image = await win.webContents.capturePage();
  return image.toPNG();
}

app.whenReady().then(async () => {
  const svg = fs.readFileSync(path.join(ASSETS, 'icon.svg'), 'utf8');

  const win = new BrowserWindow({
    width: 256,
    height: 256,
    show: false,
    frame: false,
    transparent: true,
    backgroundColor: '#00000000',
    useContentSize: true
  });

  const entries = [];
  for (const size of SIZES) {
    const data = await capture(win, size, svg);
    entries.push({ size, data });
    console.log(`  captured ${size}x${size}  ${data.length} bytes`);
  }
  win.destroy();

  fs.writeFileSync(path.join(ASSETS, 'icon.png'), entries[entries.length - 1].data);
  fs.writeFileSync(path.join(ASSETS, 'icon.ico'), buildIco(entries));
  console.log('wrote assets/icon.png and assets/icon.ico');
  app.quit();
});
