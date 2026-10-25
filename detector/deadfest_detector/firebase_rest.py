"""
Minimal Firebase REST clients (Auth + Firestore), using only the standard library.

The detector signs in as *you*, with the same Firebase Auth account as the app, and talks to
Firestore with your ID token. Every write is therefore checked by the same security rules as the
phone app; the detector holds no admin key and cannot do anything you could not do in the app.

Set FIRESTORE_EMULATOR_HOST / FIREBASE_AUTH_EMULATOR_HOST to point at the local emulators.
"""

from __future__ import annotations

import base64
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Callable


class NetworkError(Exception):
    """No connection, DNS failure, timeout: worth retrying later."""


class FirebaseError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(f"{status} {code}: {message}")
        self.status = status
        self.code = code
        self.message = message


def _request(url: str, *, method: str = "GET", body: bytes | None = None, headers: dict | None = None, timeout: float = 20) -> Any:
    req = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            err = json.loads(raw).get("error", {})
        except ValueError:
            err = {}
        if isinstance(err, dict):
            code = str(err.get("status") or err.get("message") or e.code)
            message = str(err.get("message") or e.reason)
        else:
            code, message = str(e.code), str(err)
        raise FirebaseError(e.code, code, message) from None
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        raise NetworkError(str(getattr(e, "reason", e))) from None


# ---------------------------------------------------------------------- auth

class AuthClient:
    def __init__(self, api_key: str):
        if not api_key:
            raise ValueError("No Firebase API key configured. Run the 'login' command first.")
        self.api_key = api_key
        emu = os.environ.get("FIREBASE_AUTH_EMULATOR_HOST")
        self._id_base = f"http://{emu}/identitytoolkit.googleapis.com" if emu else "https://identitytoolkit.googleapis.com"
        self._token_base = f"http://{emu}/securetoken.googleapis.com" if emu else "https://securetoken.googleapis.com"

    def sign_in(self, email: str, password: str) -> dict:
        data = _request(
            f"{self._id_base}/v1/accounts:signInWithPassword?key={self.api_key}",
            method="POST",
            body=json.dumps({"email": email, "password": password, "returnSecureToken": True}).encode(),
            headers={"Content-Type": "application/json"},
        )
        return {"uid": data["localId"], "id_token": data["idToken"], "refresh_token": data["refreshToken"],
                "expires_in": int(data.get("expiresIn", 3600)), "email": data.get("email", email)}

    def refresh(self, refresh_token: str) -> dict:
        data = _request(
            f"{self._token_base}/v1/token?key={self.api_key}",
            method="POST",
            body=urllib.parse.urlencode({"grant_type": "refresh_token", "refresh_token": refresh_token}).encode(),
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        return {"uid": data["user_id"], "id_token": data["id_token"], "refresh_token": data["refresh_token"],
                "expires_in": int(data.get("expires_in", 3600))}


class Session:
    """A signed-in user. Keeps a fresh ID token; persists only the refresh token (never the password)."""

    def __init__(self, auth: AuthClient, uid: str, refresh_token: str, email: str = "", save: Callable[[dict], None] | None = None):
        self.auth = auth
        self.uid = uid
        self.email = email
        self._refresh_token = refresh_token
        self._id_token = ""
        self._expires = 0.0
        self._save = save

    def token(self) -> str:
        if not self._id_token or time.time() > self._expires - 120:
            data = self.auth.refresh(self._refresh_token)
            self._id_token = data["id_token"]
            self._expires = time.time() + data["expires_in"]
            if data["refresh_token"] != self._refresh_token:
                self._refresh_token = data["refresh_token"]
                if self._save:
                    self._save(self.to_json())
        return self._id_token

    def to_json(self) -> dict:
        return {"uid": self.uid, "email": self.email, "refresh_token": self._refresh_token}


# ---------------------------------------------------------------------- firestore values

def encode(value: Any) -> dict:
    if value is None:
        return {"nullValue": None}
    if isinstance(value, bool):
        return {"booleanValue": value}
    if isinstance(value, int):
        return {"integerValue": str(value)}
    if isinstance(value, float):
        return {"doubleValue": value}
    if isinstance(value, str):
        return {"stringValue": value}
    if isinstance(value, (bytes, bytearray)):
        return {"bytesValue": base64.b64encode(bytes(value)).decode()}
    if isinstance(value, dict):
        return {"mapValue": {"fields": {k: encode(v) for k, v in value.items()}}}
    if isinstance(value, (list, tuple)):
        return {"arrayValue": {"values": [encode(v) for v in value]}}
    raise TypeError(f"Cannot store {type(value).__name__} in Firestore")


def decode(value: dict) -> Any:
    (kind, v), = value.items()
    if kind == "nullValue":
        return None
    if kind == "booleanValue":
        return bool(v)
    if kind == "integerValue":
        return int(v)
    if kind == "doubleValue":
        return float(v)
    if kind in ("stringValue", "referenceValue", "timestampValue"):
        return v
    if kind == "bytesValue":
        return base64.b64decode(v)
    if kind == "mapValue":
        return {k: decode(x) for k, x in v.get("fields", {}).items()}
    if kind == "arrayValue":
        return [decode(x) for x in v.get("values", [])]
    return v


def decode_fields(doc: dict) -> dict:
    return {k: decode(v) for k, v in doc.get("fields", {}).items()}


_SIMPLE_SEGMENT = re.compile(r"^[A-Za-z_][A-Za-z_0-9]*$")


def quote_segment(segment: str) -> str:
    """A field-path segment as Firestore wants it: bare if simple, else in backticks."""
    if _SIMPLE_SEGMENT.match(segment):
        return segment
    return "`" + segment.replace("\\", "\\\\").replace("`", "\\`") + "`"


# ---------------------------------------------------------------------- firestore client

class FirestoreClient:
    def __init__(self, project_id: str, token: Callable[[], str]):
        if not project_id:
            raise ValueError("No Firebase project configured. Run the 'login' command first.")
        self.project_id = project_id
        self._token = token
        emu = os.environ.get("FIRESTORE_EMULATOR_HOST")
        host = f"http://{emu}" if emu else "https://firestore.googleapis.com"
        self.db = f"projects/{project_id}/databases/(default)"
        self.base = f"{host}/v1/{self.db}/documents"

    def name(self, path: str) -> str:
        return f"{self.db}/documents/{path}"

    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {self._token()}", "Content-Type": "application/json"}

    def get(self, path: str) -> dict | None:
        try:
            doc = _request(f"{self.base}/{urllib.parse.quote(path)}", headers=self._headers())
        except FirebaseError as e:
            if e.status == 404:
                return None
            raise
        return decode_fields(doc)

    def commit(self, writes: list[dict]) -> dict:
        return _request(
            f"{self.base}:commit",
            method="POST",
            body=json.dumps({"writes": writes}).encode(),
            headers=self._headers(),
        )

    # ------------------------------------------------------------------ transactions and queries

    def begin_transaction(self) -> str:
        data = _request(f"{self.base}:beginTransaction", method="POST", body=b"{}", headers=self._headers())
        return data["transaction"]

    def get_in(self, path: str, transaction: str) -> dict | None:
        """
        Read inside a transaction (the commit fails if the document changes meanwhile). Uses
        batchGet: a plain GET with ?transaction= hangs on the Firestore emulator.
        """
        rows = _request(
            f"{self.base}:batchGet",
            method="POST",
            body=json.dumps({"documents": [self.name(path)], "transaction": transaction}).encode(),
            headers=self._headers(),
        )
        for row in rows:
            if "found" in row:
                return decode_fields(row["found"])
        return None

    def commit_in(self, writes: list[dict], transaction: str) -> dict:
        return _request(
            f"{self.base}:commit",
            method="POST",
            body=json.dumps({"writes": writes, "transaction": transaction}).encode(),
            headers=self._headers(),
        )

    def run_query(self, collection: str, order_by: str, descending: bool = True, limit: int = 60,
                  where: list[tuple[str, str, Any]] | None = None) -> list[tuple[str, dict]]:
        """(document id, fields) for a simple query. Only filters that need no composite index."""
        q: dict = {
            "from": [{"collectionId": collection}],
            "orderBy": [{"field": {"fieldPath": order_by}, "direction": "DESCENDING" if descending else "ASCENDING"}],
            "limit": limit,
        }
        if where:
            filters = [{"fieldFilter": {"field": {"fieldPath": f}, "op": op, "value": encode(v)}} for f, op, v in where]
            q["where"] = filters[0] if len(filters) == 1 else {"compositeFilter": {"op": "AND", "filters": filters}}
        rows = _request(f"{self.base}:runQuery", method="POST", body=json.dumps({"structuredQuery": q}).encode(),
                        headers=self._headers())
        out = []
        for row in rows:
            doc = row.get("document")
            if doc:
                out.append((doc["name"].rsplit("/", 1)[-1], decode_fields(doc)))
        return out

    def list_documents(self, collection: str, page_size: int = 300) -> list[tuple[str, dict]]:
        out, token = [], ""
        while True:
            url = f"{self.base}/{collection}?pageSize={page_size}" + (f"&pageToken={urllib.parse.quote(token)}" if token else "")
            data = _request(url, headers=self._headers())
            for doc in data.get("documents", []):
                out.append((doc["name"].rsplit("/", 1)[-1], decode_fields(doc)))
            token = data.get("nextPageToken", "")
            if not token:
                return out

    def update_write(self, path: str, values: dict[str, Any], must_exist: bool = True) -> dict:
        """
        Sets only the given field paths (a dict of path segments tuple -> value, or "a.b" -> value).
        Segments are quoted, so sector names with spaces or punctuation are safe.
        """
        fields: dict = {}
        mask: list[str] = []
        for key, value in values.items():
            segments = key if isinstance(key, tuple) else tuple(key.split("."))
            node = fields
            for seg in segments[:-1]:
                node = node.setdefault(seg, {})
            node[segments[-1]] = value
            mask.append(".".join(quote_segment(s) for s in segments))
        write = {
            "update": {"name": self.name(path), "fields": {k: encode(v) for k, v in fields.items()}},
            "updateMask": {"fieldPaths": mask},
        }
        if must_exist:
            write["currentDocument"] = {"exists": True}
        return write

    def increment_write(self, path: str, field: str, by: int) -> dict:
        return {"transform": {"document": self.name(path),
                              "fieldTransforms": [{"fieldPath": field, "increment": {"integerValue": str(by)}}]}}

    def create_write(self, path: str, fields: dict) -> dict:
        """A write that fails if the document already exists (idempotent uploads)."""
        return {
            "update": {"name": self.name(path), "fields": {k: encode(v) for k, v in fields.items()}},
            "currentDocument": {"exists": False},
        }
