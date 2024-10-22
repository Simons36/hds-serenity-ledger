#!/usr/bin/env python

import os
import json
import sys
import signal


# Terminal Emulator used to spawn the processes
terminal = "kitty"

# Blockchain node configuration file name
server_configs = [
    "regular_config.json", "config_test_behavior_1.json"
]

service_configs = [
    "service_config.json", "service_config_test_behavior_1.json"
]


server_config = server_configs[0]
service_config = service_configs[0]

def quit_handler(*args):
    os.system(f"pkill -i {terminal}")
    sys.exit()


# Compile classes
os.system("mvn clean install")

# Spawn blockchain nodes
with open(f"Common/src/main/resources/{server_config}") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        if key['type'] == "node":
            pid = os.fork()
            if pid == 0:
                os.system(
                    f"{terminal} sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']} {server_config} {service_config}' ; sleep 500\"")
                sys.exit()

signal.signal(signal.SIGINT, quit_handler)

while True:
    print("Type quit to quit")
    command = input(">> ")
    if command.strip() == "quit":
        quit_handler()
