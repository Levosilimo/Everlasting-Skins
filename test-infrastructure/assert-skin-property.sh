#!/usr/bin/env bash
# Asserts the server-side GameProfile property was set by base64-decoding the
# SKIN_REFRESH log line emitted by SkinRefreshHandler.task().
#
# Usage: assert-skin-property.sh <server-log-file> <expected-source>
# Expected exit code: 0 if property is valid, 1 otherwise

set -uo pipefail

LOG_FILE="${1:-logs/latest.log}"
EXPECTED_SOURCE="${2:-Notch}"

if [ ! -f "$LOG_FILE" ]; then
  echo "ERROR: log file not found: $LOG_FILE"
  exit 1
fi

# Find the SKIN_REFRESH line and extract the base64 property value
# Format: SKIN_REFRESH: profile=<name>, property=Property[name=textures,value=<base64>,signature=<base64>]
SKIN_LINE=$(grep "SKIN_REFRESH" "$LOG_FILE" | tail -1)

if [ -z "$SKIN_LINE" ]; then
  echo "FAIL: no SKIN_REFRESH line in log"
  exit 1
fi

# Extract the base64 value (the long base64 string after "value=")
BASE64_VALUE=$(echo "$SKIN_LINE" | grep -oP 'value=\K[A-Za-z0-9+/=]+' | head -1)

if [ -z "$BASE64_VALUE" ]; then
  echo "FAIL: could not extract base64 value from SKIN_REFRESH line"
  echo "Line was: $SKIN_LINE"
  exit 1
fi

# Decode the base64 value
DECODED=$(echo "$BASE64_VALUE" | base64 -d 2>/dev/null)

if [ -z "$DECODED" ]; then
  echo "FAIL: base64 decode failed"
  exit 1
fi

# Check that decoded JSON has textures.SKIN.url
if echo "$DECODED" | grep -q "textures.*SKIN.*url"; then
  echo "PASS: GameProfile property contains textures.SKIN.url"
  echo "Decoded JSON: $DECODED"
  exit 0
else
  echo "FAIL: decoded JSON does not contain textures.SKIN.url"
  echo "Decoded JSON: $DECODED"
  exit 1
fi
