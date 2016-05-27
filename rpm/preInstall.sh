#!/bin/bash
echo "Stopping jmx-collector"
service jmx-collector stop > /dev/null 2>&1

echo "Checking for existince of /etc/jmx-collector/run-user file..."
if [ ! -s "/etc/jmx-collector/run-user" ]; then
    echo "/etc/jmx-collector/run-user must exist and contain the username to run jmx-collector under (user must be the one launching JVM(s) to collect metrics from)"
    exit 1
fi
