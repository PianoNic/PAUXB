#!/data/data/com.termux/files/usr/bin/bash
# PAUXB - Initial Setup Script
# Sets up Termux + Debian proot environment with X11/VNC support

LOG_FILE="$HOME/.pauxb/setup.log"
STATUS_FILE="$HOME/.pauxb/status"
mkdir -p "$HOME/.pauxb"

log() {
    echo "[$(date '+%H:%M:%S')] $1" | tee -a "$LOG_FILE"
    echo "$1" > "$STATUS_FILE"
}

fail() {
    log "ERROR - $1"
    exit 1
}

log "PHASE:UPDATE - Updating Termux packages..."
if ! pkg update -y >> "$LOG_FILE" 2>&1; then
    fail "Failed to update Termux packages. Check your internet connection."
fi

log "PHASE:INSTALL_DEPS - Installing Termux dependencies..."
if ! pkg install -y proot-distro x11-repo >> "$LOG_FILE" 2>&1; then
    fail "Failed to install proot-distro or x11-repo."
fi
if ! pkg install -y tigervnc pulseaudio >> "$LOG_FILE" 2>&1; then
    fail "Failed to install tigervnc or pulseaudio."
fi

log "PHASE:INSTALL_DEBIAN - Installing Debian via proot-distro..."
DEBIAN_ROOT="$PREFIX/var/lib/proot-distro/installed-rootfs/debian"
if proot-distro list 2>/dev/null | grep -q "debian.*installed"; then
    log "PHASE:INSTALL_DEBIAN - Debian already installed, skipping..."
else
    proot-distro install debian >> "$LOG_FILE" 2>&1 || true
    # Verify Debian was actually installed despite possible non-zero exit code
    if [ ! -d "$DEBIAN_ROOT/etc" ]; then
        fail "Debian installation failed. Check the log at $LOG_FILE"
    fi
fi

log "PHASE:SETUP_DEBIAN - Configuring Debian environment..."
if ! proot-distro login debian -- bash -c '
export DEBIAN_FRONTEND=noninteractive

apt-get update -y
apt-get install -y --no-install-recommends \
    xvfb \
    x11vnc \
    dbus-x11 \
    xdotool \
    xterm \
    procps \
    net-tools \
    sudo \
    fonts-dejavu \
    fonts-liberation \
    ca-certificates \
    locales

# Generate locale
sed -i "s/# en_US.UTF-8/en_US.UTF-8/" /etc/locale.gen
locale-gen || true

# Create the bridge daemon directory
mkdir -p /opt/pauxb
' >> "$LOG_FILE" 2>&1; then
    fail "Failed to configure Debian environment. Check the log at $LOG_FILE"
fi

log "PHASE:INSTALL_BRIDGE - Installing PAUXB bridge daemon..."

cat > "$DEBIAN_ROOT/opt/pauxb/bridge.sh" << 'BRIDGE'
#!/bin/bash
# PAUXB Bridge Daemon - manages Linux app displays
PAUXB_DIR="/opt/pauxb"
APPS_DIR="$PAUXB_DIR/apps"
PIDS_DIR="$PAUXB_DIR/pids"
SOCKET_DIR="/tmp/pauxb"

mkdir -p "$APPS_DIR" "$PIDS_DIR" "$SOCKET_DIR"

# Base VNC display number
BASE_DISPLAY=10

get_next_display() {
    local d=$BASE_DISPLAY
    while [ -f "$PIDS_DIR/display_$d.pid" ]; do
        if ! kill -0 "$(cat "$PIDS_DIR/display_$d.pid")" 2>/dev/null; then
            rm -f "$PIDS_DIR/display_$d.pid"
            break
        fi
        d=$((d + 1))
    done
    echo $d
}

cmd_start() {
    local app_cmd="$1"
    local app_id="$2"
    local width="${3:-1280}"
    local height="${4:-720}"

    if [ -z "$app_cmd" ] || [ -z "$app_id" ]; then
        echo "ERROR: Usage: start <command> <app_id> [width] [height]"
        return 1
    fi

    # Check if already running
    if [ -f "$PIDS_DIR/${app_id}.pid" ] && kill -0 "$(cat "$PIDS_DIR/${app_id}.pid")" 2>/dev/null; then
        local existing_port=$(cat "$PIDS_DIR/${app_id}.port" 2>/dev/null)
        echo "RUNNING:$existing_port"
        return 0
    fi

    local display=$(get_next_display)
    local vnc_port=$((5900 + display))

    # Start Xvfb
    Xvfb :$display -screen 0 ${width}x${height}x24 &
    local xvfb_pid=$!
    echo $xvfb_pid > "$PIDS_DIR/display_$display.pid"
    sleep 1

    # Start x11vnc on that display
    x11vnc -display :$display -rfbport $vnc_port -nopw -forever -shared -noxdamage -noxfixes -nowf &
    local vnc_pid=$!
    sleep 1

    # Start the app
    DISPLAY=:$display $app_cmd &
    local app_pid=$!

    # Save state
    echo $app_pid > "$PIDS_DIR/${app_id}.pid"
    echo $vnc_port > "$PIDS_DIR/${app_id}.port"
    echo $display > "$PIDS_DIR/${app_id}.display"
    echo $xvfb_pid > "$PIDS_DIR/${app_id}.xvfb_pid"
    echo $vnc_pid > "$PIDS_DIR/${app_id}.vnc_pid"

    echo "STARTED:$vnc_port:$display"
}

cmd_stop() {
    local app_id="$1"
    for suffix in pid xvfb_pid vnc_pid; do
        local pidfile="$PIDS_DIR/${app_id}.${suffix}"
        if [ -f "$pidfile" ]; then
            kill "$(cat "$pidfile")" 2>/dev/null
            rm -f "$pidfile"
        fi
    done
    local display=$(cat "$PIDS_DIR/${app_id}.display" 2>/dev/null)
    rm -f "$PIDS_DIR/${app_id}.port" "$PIDS_DIR/${app_id}.display"
    [ -n "$display" ] && rm -f "$PIDS_DIR/display_$display.pid"
    echo "STOPPED:$app_id"
}

cmd_list() {
    for pidfile in "$PIDS_DIR"/*.pid; do
        [ -f "$pidfile" ] || continue
        local name=$(basename "$pidfile" .pid)
        [[ "$name" == display_* ]] && continue
        local port=$(cat "$PIDS_DIR/${name}.port" 2>/dev/null)
        local pid=$(cat "$pidfile")
        if kill -0 "$pid" 2>/dev/null; then
            echo "APP:$name:$port:running"
        else
            echo "APP:$name:$port:dead"
            rm -f "$PIDS_DIR/${name}".*
        fi
    done
}

cmd_resize() {
    local app_id="$1"
    local width="$2"
    local height="$3"
    local display=$(cat "$PIDS_DIR/${app_id}.display" 2>/dev/null)
    if [ -n "$display" ]; then
        xdotool set-desktop-viewport --screen $display 0 0 2>/dev/null
        DISPLAY=:$display xrandr --fb ${width}x${height} 2>/dev/null || true
        echo "RESIZED:$app_id:${width}x${height}"
    else
        echo "ERROR:not_found"
    fi
}

# Command socket listener
SOCKET="$SOCKET_DIR/bridge.sock"
rm -f "$SOCKET"

# Simple FIFO-based command interface
FIFO_IN="$SOCKET_DIR/cmd_in"
FIFO_OUT="$SOCKET_DIR/cmd_out"
rm -f "$FIFO_IN" "$FIFO_OUT"
mkfifo "$FIFO_IN" "$FIFO_OUT"

echo "BRIDGE_READY" > "$SOCKET_DIR/status"

while true; do
    if read -r line < "$FIFO_IN"; then
        cmd=$(echo "$line" | cut -d' ' -f1)
        args=$(echo "$line" | cut -d' ' -f2-)
        case "$cmd" in
            start)
                result=$(cmd_start $args)
                ;;
            stop)
                result=$(cmd_stop $args)
                ;;
            list)
                result=$(cmd_list)
                ;;
            resize)
                result=$(cmd_resize $args)
                ;;
            quit)
                echo "QUIT" > "$FIFO_OUT"
                exit 0
                ;;
            *)
                result="ERROR:unknown_command"
                ;;
        esac
        echo "$result" > "$FIFO_OUT"
    fi
done
BRIDGE

chmod +x "$DEBIAN_ROOT/opt/pauxb/bridge.sh"

# Create a helper script to send commands to the bridge
cat > "$DEBIAN_ROOT/opt/pauxb/bridge-cmd.sh" << 'BCMD'
#!/bin/bash
SOCKET_DIR="/tmp/pauxb"
echo "$*" > "$SOCKET_DIR/cmd_in"
cat "$SOCKET_DIR/cmd_out"
BCMD
chmod +x "$DEBIAN_ROOT/opt/pauxb/bridge-cmd.sh"

log "PHASE:COMPLETE - Setup complete!"
echo "SETUP_COMPLETE" > "$STATUS_FILE"
