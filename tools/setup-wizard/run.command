#!/usr/bin/env bash
# macOS launcher — double-click in Finder, or run from a terminal.
cd "$(dirname "$0")" || exit 1
exec python3 setup_wizard.py
