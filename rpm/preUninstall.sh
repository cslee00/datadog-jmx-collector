#!/bin/bash
echo "Uninstalling jmx-collector"
service jmx-collector stop > /dev/null 2>&1
chkconfig jmx-collector off > /dev/null 2>&1