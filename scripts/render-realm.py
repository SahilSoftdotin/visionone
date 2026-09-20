#!/usr/bin/env python3
"""Render the local realm into one that can face the internet.

The realm in git is the development realm: it redirects to localhost:5173, accepts plain HTTP, and
carries throwaway passwords so a new clone can sign in immediately. Every one of those is wrong on
a public host, and none of them should be edited in place - local development would break and the
real passwords would land in git.

So this reads the committed realm, substitutes what the environment dictates, and writes the result
to infra/keycloak/generated/, which is ignored. Nothing secret is ever written to a tracked file.

Usage:  DOMAIN=demo.example.com VISIONONE_DEMO_PASSWORD_SAHIL=... python scripts/render-realm.py
"""

import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "infra" / "keycloak" / "visionone-realm.json"
OUT_DIR = ROOT / "infra" / "keycloak" / "generated"

# Each demo account takes its password from its own variable. Deliberately no defaults: a missing
# one stops the render rather than quietly publishing "vision123" on a public URL.
PASSWORD_VARS = {
    "sahil": "VISIONONE_DEMO_PASSWORD_SAHIL",
    "gary": "VISIONONE_DEMO_PASSWORD_GARY",
}

MIN_PASSWORD_LEN = 12


def fail(message: str) -> None:
    print(f"render-realm: {message}", file=sys.stderr)
    sys.exit(1)


def main() -> None:
    domain = os.environ.get("DOMAIN", "").strip()
    if not domain:
        fail("DOMAIN is not set")
    if domain.startswith("http"):
        fail(f"DOMAIN should be a bare hostname, not a URL (got {domain!r})")

    realm = json.loads(SOURCE.read_text(encoding="utf-8"))
    origin = f"https://{domain}"

    # Keycloak refuses to issue cookies over plain HTTP once this is set, which is what we want the
    # moment the realm is reachable from anywhere but this machine.
    realm["sslRequired"] = "external"

    for client in realm.get("clients", []):
        if client.get("clientId") == "visionone-web":
            client["redirectUris"] = [f"{origin}/*"]
            client["webOrigins"] = [origin]
            client["rootUrl"] = origin

    missing, weak = [], []
    for user in realm.get("users", []):
        var = PASSWORD_VARS.get(user.get("username"))
        if var is None:
            continue
        value = os.environ.get(var, "")
        if not value:
            missing.append(var)
            continue
        if len(value) < MIN_PASSWORD_LEN:
            weak.append(f"{var} (needs {MIN_PASSWORD_LEN}+ characters)")
            continue
        user["credentials"] = [{"type": "password", "value": value, "temporary": False}]

    if missing:
        fail("not set: " + ", ".join(missing))
    if weak:
        fail("too short: " + ", ".join(weak))

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    target = OUT_DIR / "visionone-realm.json"
    target.write_text(json.dumps(realm, indent=2) + "\n", encoding="utf-8")

    print(f"render-realm: wrote {target.relative_to(ROOT)}")
    print(f"render-realm: redirect {origin}/*  sslRequired=external  "
          f"{len(PASSWORD_VARS)} account passwords replaced")


if __name__ == "__main__":
    main()
