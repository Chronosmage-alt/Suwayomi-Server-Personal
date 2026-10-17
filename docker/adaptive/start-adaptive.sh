#!/bin/sh
set -eu

ui_root=/home/suwayomi/.local/share/Tachidesk/webUI
mkdir -p "$ui_root"
cp -R /opt/adaptive-webui/. "$ui_root/"
exec /home/suwayomi/startup_script.sh
