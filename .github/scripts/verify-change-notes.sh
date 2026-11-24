#!/usr/bin/env bash
#
# Copyright (c) KleinerHacker alias Pfeiffer C Soft 2026.
# This work is licensed under the Apache License, Version 2.0.
# You may not use this file except in compliance with the License.
# You may obtain a copy of the License at:
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, this software is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and limitations.
#

# Verifies that the patched plugin descriptor carries non-empty <change-notes>.
#
# The JetBrains Marketplace ("What's New") and the IDE's plugin manager show ONLY the <change-notes> of the
# packaged plugin.xml — they never read CHANGELOG.md. The element is optional, so the Marketplace accepts an
# upload without it silently. This check turns a missing or empty element into a failed build instead.
#
# Usage: verify-change-notes.sh [path/to/patched/plugin.xml]
#        (default: plugin/build/tmp/patchPluginXml/plugin.xml, the output of :plugin:patchPluginXml)

set -euo pipefail

DESCRIPTOR="${1:-plugin/build/tmp/patchPluginXml/plugin.xml}"

if [ ! -f "$DESCRIPTOR" ]; then
  echo "ERROR: patched plugin descriptor not found: $DESCRIPTOR (did :plugin:patchPluginXml run?)"
  exit 1
fi

# Extract the element body (it may span several lines and be wrapped in CDATA), strip the CDATA markers and
# all whitespace — what remains must not be empty.
NOTES=$(perl -0777 -ne 'print $1 if /<change-notes>(.*?)<\/change-notes>/s' "$DESCRIPTOR" \
  | sed -e 's/<!\[CDATA\[//g' -e 's/\]\]>//g' | tr -d '[:space:]')

if [ -z "$NOTES" ]; then
  echo "ERROR: $DESCRIPTOR contains no or empty <change-notes>."
  echo "       The Marketplace would show nothing under \"What's New\". Check the CHANGELOG.md section of the"
  echo "       built version and the changeNotes wiring in plugin/build.gradle.kts."
  exit 1
fi

echo "Change notes present in $DESCRIPTOR (${#NOTES} characters without whitespace)."
