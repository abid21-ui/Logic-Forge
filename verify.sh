#!/usr/bin/env sh
set -eu

if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven is required. Install Maven 3.9+ and run this script again." >&2
    exit 1
fi

mvn --batch-mode --no-transfer-progress clean test
