/* =============================================================================
   Dead-Fest desktop — image pipeline
   -----------------------------------------------------------------------------
   A port of util/ImageCompressor.kt. A picked photo becomes two JPEGs: a full
   image that must fit comfortably inside a Firestore document, and a small
   thumbnail that rides along with the gallery metadata.

   The numbers are the Android ones on purpose — the same 1600px long edge, the
   same quality ladder, the same 700 KB ceiling, the same 320px thumbnail — so a
   photo posted from the desktop looks like a photo posted from a phone.
   ========================================================================== */
window.DF = window.DF || {};

DF.Media = (function () {
  const FULL_MAX_DIM = 1600;
  const THUMB_MAX_DIM = 320;
  const MAX_FULL_BYTES = 700000;
  const FULL_QUALITY = 82;
  const MIN_QUALITY = 52;
  const QUALITY_STEP = 10;
  const THUMB_QUALITY = 65;

  /** Decodes a File into an ImageBitmap (or an Image, on engines without the API). */
  function decode(file) {
    if (typeof createImageBitmap === "function") return createImageBitmap(file);
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const img = new Image();
      img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
      img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("Couldn't read that image.")); };
      img.src = url;
    });
  }

  function canvasFor(width, height) {
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(width));
    canvas.height = Math.max(1, Math.round(height));
    const ctx = canvas.getContext("2d");
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";
    return { canvas, ctx };
  }

  /** Scales so the longest side is at most `maxDim`. Never enlarges. */
  function scaleTo(source, maxDim) {
    const w = source.width || source.naturalWidth;
    const h = source.height || source.naturalHeight;
    const longest = Math.max(w, h);
    const scale = longest > maxDim ? maxDim / longest : 1;
    const outW = Math.max(1, Math.round(w * scale));
    const outH = Math.max(1, Math.round(h * scale));
    const { canvas, ctx } = canvasFor(outW, outH);
    ctx.drawImage(source, 0, 0, outW, outH);
    return { canvas, width: outW, height: outH };
  }

  function toJpeg(canvas, quality) {
    return new Promise((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (!blob) { reject(new Error("Couldn't compress that image.")); return; }
        blob.arrayBuffer().then((buf) => resolve(new Uint8Array(buf)), reject);
      }, "image/jpeg", quality / 100);
    });
  }

  /**
   * Compresses a picked image exactly the way the phone does: quality steps down first, then the
   * image itself shrinks, and the loop stops as soon as it fits — or at 640px, whichever comes
   * first. A 50 MP photo is never decoded at full size, so this can't run the window out of memory.
   */
  function process(file) {
    return decode(file).then((bitmap) => {
      let scaled = scaleTo(bitmap, FULL_MAX_DIM);
      let quality = FULL_QUALITY;
      let full = null;
      return toJpeg(scaled.canvas, quality).then(function step(bytes) {
        full = bytes;
        if (bytes.length <= MAX_FULL_BYTES) return null;
        if (quality - QUALITY_STEP >= MIN_QUALITY) {
          quality -= QUALITY_STEP;
          return toJpeg(scaled.canvas, quality).then(step);
        }
        let dim = Math.max(scaled.width, scaled.height);
        if (dim <= 640) return null;
        dim = Math.round(dim * 0.75);
        scaled = scaleTo(scaled.canvas, dim);
        return toJpeg(scaled.canvas, quality).then(step);
      }).then(() => {
        const thumb = scaleTo(scaled.canvas, THUMB_MAX_DIM);
        return toJpeg(thumb.canvas, THUMB_QUALITY).then((thumbBytes) => {
          if (typeof bitmap.close === "function") bitmap.close();
          return {
            full,
            thumb: thumbBytes,
            width: scaled.width,
            height: scaled.height,
            sizeBytes: full.length
          };
        });
      });
    });
  }

  /* ---- object URLs --------------------------------------------------------
     One URL per byte array, cached by key, so a re-render of the grid does not
     leak a new blob URL every frame. */
  const urls = new Map();

  function objectUrl(key, bytes, mime) {
    const existing = urls.get(key);
    if (existing) return existing;
    const url = URL.createObjectURL(new Blob([bytes], { type: mime || "image/jpeg" }));
    urls.set(key, url);
    // Keep the cache small: a busy gallery would otherwise hold every photo for the session.
    if (urls.size > 400) {
      const oldest = urls.keys().next().value;
      URL.revokeObjectURL(urls.get(oldest));
      urls.delete(oldest);
    }
    return url;
  }

  return { process, objectUrl, FULL_MAX_DIM, THUMB_MAX_DIM, MAX_FULL_BYTES };
})();
