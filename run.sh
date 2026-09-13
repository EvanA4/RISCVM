#!/usr/bin/env bash
set -euo pipefail

args=""

for arg in "$@"; do
    printf -v quoted_arg '%q' "$arg"
    if [[ -n "$args" ]]; then
        args+=" "
    fi
    args+="$quoted_arg"
done

exec ./gradlew run --args="$args"