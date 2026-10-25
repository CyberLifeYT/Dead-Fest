"""Image encoding: lossless PNG for the local evidence, size-capped JPEGs for sharing."""

from __future__ import annotations

import cv2
import numpy as np

THUMB_MAX_BYTES = 90 * 1024      # Firestore rule: thumb < 100 KB
FULL_MAX_BYTES = 850 * 1024      # Firestore rule: blob < 900 KB


def png(frame_bgr: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".png", frame_bgr, [cv2.IMWRITE_PNG_COMPRESSION, 3])
    if not ok:
        raise ValueError("PNG encoding failed")
    return buf.tobytes()


def decode(data: bytes) -> np.ndarray:
    img = cv2.imdecode(np.frombuffer(data, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Not a readable image")
    return img


def _fit(frame: np.ndarray, max_dim: int) -> np.ndarray:
    h, w = frame.shape[:2]
    scale = min(1.0, max_dim / max(h, w))
    if scale >= 1.0:
        return frame
    return cv2.resize(frame, (max(1, int(w * scale)), max(1, int(h * scale))), interpolation=cv2.INTER_AREA)


def jpeg_capped(frame_bgr: np.ndarray, max_dim: int, max_bytes: int, start_quality: int = 85) -> tuple[bytes, int, int]:
    """JPEG under max_bytes: lowers quality, then size, until it fits. Returns (bytes, width, height)."""
    img = _fit(frame_bgr, max_dim)
    while True:
        for q in range(start_quality, 34, -10):
            ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, q])
            if ok and len(buf) <= max_bytes:
                return buf.tobytes(), img.shape[1], img.shape[0]
        if max(img.shape[:2]) < 160:
            raise ValueError("Image cannot be made small enough")
        img = _fit(img, int(max(img.shape[:2]) * 0.75))


def thumbnail(frame_bgr: np.ndarray) -> bytes:
    return jpeg_capped(frame_bgr, 480, THUMB_MAX_BYTES, 70)[0]


def full(frame_bgr: np.ndarray) -> tuple[bytes, int, int]:
    return jpeg_capped(frame_bgr, 1600, FULL_MAX_BYTES, 85)
