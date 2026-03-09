#!/data/data/com.termux/files/usr/bin/bash
# Send a command to the PAUXB bridge daemon
proot-distro login debian -- bash /opt/pauxb/bridge-cmd.sh "$@"
