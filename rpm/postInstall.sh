#!/bin/bash
cd /opt/jmx-collector-$RPM_PACKAGE_VERSION
echo "Extracting JDK `ls jdk*.gz` in $PWD"
tar zxf jdk*.gz
rm -f jdk*.gz

mkdir /var/log/jmx-collector > /dev/null 2>&1

USER=`cat /etc/jmx-collector/run-user`
echo "Adjusting ownership to $USER"
chown $USER:$USER -R . /var/log/jmx-collector

rm -f /opt/jmx-collector
ln -s /opt/jmx-collector-$RPM_PACKAGE_VERSION /opt/jmx-collector

echo "Turning on chkconfig"
chkconfig jmx-collector on