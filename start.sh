#!/bin/bash
# Keeps the server alive: restarts after /stop, HavenCore restarts and crashes.
# "stop force" in console creates a file called no-restart to really stop it.
while true; do
  java -Xms4G -Xmx4G -jar server.jar nogui
  if [ -f no-restart ]; then
    rm -f no-restart
    echo "Stopped (no-restart file found)."
    break
  fi
  echo "Server stopped. Restarting in 5 seconds... (Ctrl+C to cancel)"
  sleep 5
done
