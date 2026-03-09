#!/data/data/com.termux/files/usr/bin/bash
# Check PAUXB setup/bridge status
if [ -f "$HOME/.pauxb/status" ]; then
    cat "$HOME/.pauxb/status"
else
    echo "NOT_SETUP"
fi
