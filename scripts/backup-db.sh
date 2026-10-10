#!/usr/bin/env bash
#
# Nightly Postgres dump for the VisionOne server.
#
# Why this exists at all, given the database is still mostly a synthetic seed: the channel
# attribution Vision records by hand on the Calendar is held in VisionOne and nowhere else.
# Healthie does not have it, the ad platforms do not have it, and it cannot be recomputed. A lost
# volume loses it permanently. Everything else in here could be re-synced from an upstream
# provider; that one column could not.
#
# Deliberately a dump rather than a volume snapshot. A dump restores into a different Postgres
# version and can be inspected before it is trusted, and it is the only form that answers "what
# did this row say last Tuesday" without taking the service down.
#
# Installed by scripts/provision-server.sh as a cron job. Safe to run by hand at any time.
#
# Usage:  scripts/backup-db.sh [destination-dir]

set -euo pipefail

STACK_DIR="${STACK_DIR:-/opt/visionone}"
DEST="${1:-/var/backups/visionone}"
KEEP_DAYS="${KEEP_DAYS:-14}"

cd "$STACK_DIR"

# shellcheck disable=SC1091
set -a; . infra/.env.prod; set +a

COMPOSE=(docker compose --env-file infra/.env.prod -f infra/docker-compose.prod.yml)

mkdir -p "$DEST"
# The dump contains every row in the database. Nobody but root has any business reading it.
chmod 700 "$DEST"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$DEST/visionone-$stamp.sql.gz"

# Write to a .partial first and rename only on success. Without this, a dump interrupted halfway
# leaves a file that looks like a backup, is half a database, and is indistinguishable from a good
# one until the day you need it.
tmp="$target.partial"

if ! "${COMPOSE[@]}" exec -T postgres \
        pg_dump -U visionone -d visionone --clean --if-exists </dev/null | gzip -9 > "$tmp"; then
    rm -f "$tmp"
    echo "visionone-backup: pg_dump FAILED at $stamp" >&2
    exit 1
fi

# gzip reports a truncated stream, which catches a dump that died mid-pipe while still exiting 0.
if ! gzip -t "$tmp"; then
    rm -f "$tmp"
    echo "visionone-backup: dump at $stamp is not a valid gzip stream, discarded" >&2
    exit 1
fi

# An empty or tiny dump means pg_dump connected and produced nothing useful. 10 KB is far below a
# schema-only dump of 19 tables, so anything under it is a failure wearing a success's clothes.
size="$(stat -c %s "$tmp")"
if [ "$size" -lt 10240 ]; then
    rm -f "$tmp"
    echo "visionone-backup: dump at $stamp was only $size bytes, discarded" >&2
    exit 1
fi

mv "$tmp" "$target"
chmod 600 "$target"

# Prune old dumps, and only ever dumps this script wrote - the glob is deliberately narrow so a
# misconfigured DEST cannot turn this into a general-purpose file remover.
find "$DEST" -maxdepth 1 -name 'visionone-*.sql.gz' -type f -mtime "+$KEEP_DAYS" -delete
find "$DEST" -maxdepth 1 -name 'visionone-*.sql.gz.partial' -type f -mtime +1 -delete

echo "visionone-backup: wrote $target ($(numfmt --to=iec "$size")), keeping $KEEP_DAYS days"
