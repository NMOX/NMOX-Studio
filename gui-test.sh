#!/bin/bash

# NMOX Studio GUI Testing Script
# This script automates GUI testing using AppleScript

echo "NMOX Studio GUI Testing"
echo "========================"

# Function to take a screenshot
take_screenshot() {
    local name=$1
    screencapture -x "/tmp/nmox-$name.png"
    echo "Screenshot saved: /tmp/nmox-$name.png"
}

# Function to click menu item
click_menu() {
    local menu=$1
    local item=$2
    osascript -e "tell application \"System Events\" to tell process \"java\"
        click menu item \"$item\" of menu \"$menu\" of menu bar 1
    end tell"
    echo "Clicked: $menu > $item"
}

# Function to use keyboard shortcut
keyboard_shortcut() {
    local keys=$1
    osascript -e "tell application \"System Events\" to tell process \"java\"
        keystroke \"$keys\" using command down
    end tell"
    echo "Pressed: Cmd+$keys"
}

# Ensure NMOX Studio is in focus
echo "Focusing NMOX Studio..."
osascript -e 'tell application "System Events" to set frontmost of (first process whose name contains "java") to true'
sleep 1

# Take initial screenshot
take_screenshot "initial"

# Test 1: Open File menu
echo ""
echo "Test 1: Opening File menu..."
click_menu "File" "New Project..."
sleep 2
take_screenshot "new-project"

# Test 2: Open Window menu to show Project Explorer
echo ""
echo "Test 2: Opening Project Explorer..."
osascript -e 'tell application "System Events" to tell process "java"
    click menu item "Projects" of menu "Window" of menu bar 1
end tell'
sleep 2
take_screenshot "project-explorer"

# Test 3: Open View menu
echo ""
echo "Test 3: Checking View menu..."
osascript -e 'tell application "System Events" to tell process "java"
    click menu "View" of menu bar 1
end tell'
sleep 1
take_screenshot "view-menu"

# Test 4: Open Tools menu
echo ""
echo "Test 4: Checking Tools menu..."
osascript -e 'tell application "System Events" to tell process "java"
    click menu "Tools" of menu bar 1
end tell'
sleep 1
take_screenshot "tools-menu"

echo ""
echo "GUI tests completed!"
echo "Screenshots saved in /tmp/nmox-*.png"