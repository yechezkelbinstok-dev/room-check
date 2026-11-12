#!/usr/bin/env bash
# Re-renders final_transparent.png, pieces/ and layout.json from the files in rebuild/.
set -e
cd "$(dirname "$0")"
pip install -q -r rebuild/requirements.txt
if ! fc-list | grep -q FrankRuehl; then apt-get install -y -q culmus >/dev/null 2>&1 || sudo apt-get install -y -q culmus >/dev/null 2>&1 || true; fi
if fc-list | grep -q FrankRuehl; then (cd rebuild/build && python3 reletter.py); else echo "Frank Ruehl font missing (apt install culmus): using the pre-made re-lettered covers"; fi
cd rebuild/build && PIECES=../../pieces python3 build4.py 4.0 ../../final.png 1
cd ../.. && rm -f final.png && echo "done: final_transparent.png, pieces/, layout.json"
