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
from typing import Optional

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "infra" / "keycloak" / "visionone-realm.json"
OUT_DIR = ROOT / "infra" / "keycloak" / "generated"

# Each demo account takes its password from its own variable. Deliberately no defaults: a missing
# one stops the render rather than quietly publishing "vision123" on a public URL.
PASSWORD_VARS = {
    "sahil": "VISIONONE_DEMO_PASSWORD_SAHIL",
    "garyadams": "VISIONONE_DEMO_PASSWORD_GARY",
}

# Where a password reset actually lands. These are optional overrides rather than committed values,
# because the addresses are not ours to publish - Gary's is a personal mailbox and this repository
# is public. The committed realm keeps a role address as its default so a fresh clone still works
# with nothing configured.
EMAIL_VARS = {
    "sahil": "VISIONONE_DEMO_EMAIL_SAHIL",
    "garyadams": "VISIONONE_DEMO_EMAIL_GARY",
}

# Eight, not twelve. Both accounts are ours and the realm has brute-force lockout enabled below, so
# this floor exists to catch an empty or obviously accidental value, not to overrule a password the
# account holder chose. The higher floor only ever showed up as a deploy that refused to start.
MIN_PASSWORD_LEN = 8


def fail(message: str) -> None:
    print(f"render-realm: {message}", file=sys.stderr)
    sys.exit(1)


def smtp_server() -> Optional[dict]:
    """The outbound mail configuration, or None when no credential is available.

    Resend over SMTP. The password is the Resend API key and the username is the literal string
    "resend" - that is Resend's own scheme, not a placeholder someone forgot to fill in.
    """
    password = os.environ.get("VISIONONE_SMTP_PASSWORD", "").strip()
    if not password:
        return None
    return {
        "host": os.environ.get("VISIONONE_SMTP_HOST", "smtp.resend.com").strip(),
        "port": os.environ.get("VISIONONE_SMTP_PORT", "587").strip(),
        "starttls": "true",
        "ssl": "false",
        "auth": "true",
        "user": os.environ.get("VISIONONE_SMTP_USER", "resend").strip(),
        "password": password,
        # Must be a domain verified with Resend, or every send is rejected outright.
        "from": os.environ.get("VISIONONE_SMTP_FROM", "noreply@visiondigitallab.com").strip(),
        "fromDisplayName": "VisionOne",
        # Someone replying to a password-reset mail should reach a human, not a dead no-reply box.
        "replyTo": os.environ.get("VISIONONE_SMTP_REPLY_TO", "hello@visiondigitallab.com").strip(),
    }


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

    # Lock out password guessing. Keycloak's default is OFF, so without this the sign-in page is an
    # unlimited guessing endpoint the moment it has a public hostname. Temporary lockout rather than
    # permanent: permanentLockout lets anyone lock Gary out of his own dashboard by guessing badly
    # at his username thirty times.
    realm["bruteForceProtected"] = True
    realm["permanentLockout"] = False
    realm["failureFactor"] = 10
    realm["waitIncrementSeconds"] = 60
    realm["maxFailureWaitSeconds"] = 900

    # "Forgot password?" is rendered whenever resetPasswordAllowed is true, and clicking it sends
    # mail. So the link and the mail server move together: configure SMTP and the link works, leave
    # it unconfigured and the link is not offered at all. Keycloak will otherwise render a reset
    # link with nowhere to send to and fail in the user's face, which is how this read before
    # Resend was set up.
    smtp = smtp_server()
    if smtp:
        realm["smtpServer"] = smtp
        realm["resetPasswordAllowed"] = True
    else:
        realm.pop("smtpServer", None)
        realm["resetPasswordAllowed"] = False

    for client in realm.get("clients", []):
        if client.get("clientId") == "visionone-web":
            client["redirectUris"] = [f"{origin}/*"]
            client["webOrigins"] = [origin]
            client["rootUrl"] = origin
            # Sign-out is validated against its own list, not redirectUris. Miss this and the
            # Sign Out button lands on Keycloak's "Invalid redirect uri" page - a failure that
            # only ever appears on the deployed host, because locally the dev value is correct.
            client.setdefault("attributes", {})["post.logout.redirect.uris"] = f"{origin}/*"
            # No password grant in production. It is on locally because it makes a token
            # obtainable with curl, but on a public host it is a password-guessing endpoint that
            # skips the login page entirely - so it skips the theme, the flow, and anything the
            # browser gives us. The app uses the authorization code flow with PKCE and never
            # needs this. Verify a deployed sign-in in a browser instead.
            client["directAccessGrantsEnabled"] = False

    missing, weak = [], []
    emails_overridden = 0
    for user in realm.get("users", []):
        username = user.get("username")

        email = os.environ.get(EMAIL_VARS.get(username, ""), "").strip()
        if email:
            user["email"] = email
            # Set here rather than left to Keycloak's default: an unverified address cannot
            # receive a password reset, and these two addresses we have confirmed ourselves.
            user["emailVerified"] = True
            emails_overridden += 1

        var = PASSWORD_VARS.get(username)
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
          f"{len(PASSWORD_VARS)} account passwords replaced, {emails_overridden} emails overridden")
    # Said out loud, because a silently missing mail server is the whole difference between a
    # working "Forgot password?" link and one that is not offered at all.
    if smtp:
        print("render-realm: SMTP via {}:{} as {}  resetPasswordAllowed=true".format(
            smtp["host"], smtp["port"], smtp["from"]))
    else:
        print("render-realm: no VISIONONE_SMTP_PASSWORD - resetPasswordAllowed=false, "
              "no 'Forgot password?' link")


if __name__ == "__main__":
    main()
