#!/data/data/com.termux/files/usr/bin/bash
# Start the PAUXB bridge daemon inside Debian proot
proot-distro login debian -- bash /opt/pauxb/bridge.sh &
echo $! > "$HOME/.pauxb/bridge.pid"
echo "BRIDGE_STARTED"
