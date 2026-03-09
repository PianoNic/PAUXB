#!/bin/bash
# PAUXB - List installed GUI applications from .desktop files
# Outputs JSON-like format: NAME|EXEC|ICON|PACKAGE

DIRS="/usr/share/applications /usr/local/share/applications"

for dir in $DIRS; do
    [ -d "$dir" ] || continue
    for desktop in "$dir"/*.desktop; do
        [ -f "$desktop" ] || continue

        name=""
        exec_cmd=""
        icon=""
        no_display=""
        terminal=""

        while IFS='=' read -r key value; do
            case "$key" in
                Name) [ -z "$name" ] && name="$value" ;;
                Exec) [ -z "$exec_cmd" ] && exec_cmd="$value" ;;
                Icon) [ -z "$icon" ] && icon="$value" ;;
                NoDisplay) no_display="$value" ;;
                Terminal) terminal="$value" ;;
            esac
        done < "$desktop"

        # Skip entries marked as hidden or terminal-only
        [ "$no_display" = "true" ] && continue

        # Strip field codes from Exec (%f, %F, %u, %U, etc.)
        exec_cmd=$(echo "$exec_cmd" | sed 's/ %[fFuUdDnNickvm]//g')

        [ -z "$name" ] || [ -z "$exec_cmd" ] && continue

        # Try to find which package owns this desktop file
        pkg=$(dpkg -S "$desktop" 2>/dev/null | cut -d: -f1 || echo "unknown")

        echo "${name}|${exec_cmd}|${icon}|${pkg}"
    done
done
