#!/usr/bin/env bash
#
# Creates a demo PDF from a JSON payload, to check a template quickly without
# going through a calling application.
#
# Usage: ./create_demo_pdf.sh [payload.json] [--no-open]
#   payload.json  Default: formatum_demo_payload.json (relative to the script)
#   --no-open     Do not open the PDF in a viewer
#   FORMATUM_HOST Default: http://127.0.0.1:8181

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
HOST="${FORMATUM_HOST:-http://127.0.0.1:8181}"
ENDPOINT="/api/documents"
OUT_DIR="$SCRIPT_DIR/out"

PAYLOAD="formatum_demo_payload.json"
OPEN_PDF=1
for arg in "$@"; do
    case "$arg" in
        --no-open) OPEN_PDF=0 ;;
        -h|--help) sed -n '3,10p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) PAYLOAD="$arg" ;;
    esac
done
[[ "$PAYLOAD" = /* ]] || PAYLOAD="$SCRIPT_DIR/$PAYLOAD"

if [ -t 1 ]; then RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[1;33m'; NC=$'\033[0m'
else RED=; GREEN=; YELLOW=; NC=; fi
die() { echo "${RED}❌ $*${NC}" >&2; exit 1; }

[ -f "$PAYLOAD" ] || die "Payload not found: $PAYLOAD"

echo "${YELLOW}🚀 Formatum PDF conversion${NC}  ${HOST}${ENDPOINT}  ←  $(basename "$PAYLOAD")"

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

HTTP_CODE=$(curl -sS -m 120 -w '%{http_code}' \
    -X POST \
    -H 'Content-Type: application/json' \
    --data-binary @"$PAYLOAD" \
    -o "$TMP" \
    "${HOST}${ENDPOINT}") || die "Cannot reach Formatum at $HOST. Is it running (./run.sh)?"

if [ "$HTTP_CODE" != 200 ]; then
    echo "${RED}❌ HTTP $HTTP_CODE${NC}, response:" >&2
    # Errors are JSON; only use jq when it is available
    if command -v jq >/dev/null && jq . "$TMP" >/dev/null 2>&1; then jq . "$TMP" >&2; else cat "$TMP" >&2; fi
    exit 1
fi

[ "$(head -c 4 "$TMP")" = '%PDF' ] || {
    echo "${RED}❌ HTTP 200, but not a PDF. First 500 bytes:${NC}" >&2
    head -c 500 "$TMP" >&2; echo >&2
    exit 1
}

mkdir -p "$OUT_DIR"
OUTPUT="$OUT_DIR/$(basename "$PAYLOAD" .json)-$(date +%Y%m%d-%H%M%S).pdf"
mv "$TMP" "$OUTPUT"
trap - EXIT
chmod 644 "$OUTPUT"

echo "${GREEN}✅ $OUTPUT${NC} ($(du -h "$OUTPUT" | cut -f1))"

if [ "$OPEN_PDF" = 1 ] && command -v xdg-open >/dev/null; then
    xdg-open "$OUTPUT" >/dev/null 2>&1 &
fi