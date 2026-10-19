#!/bin/bash
# restore.sh d0|d10 : force-stop and restore a datastore snapshot
export ANDROID_SERIAL=emulator-5554
adb shell "am force-stop com.superchant; U=\$(stat -c %u /data/data/com.superchant); rm -rf /data/data/com.superchant/files/datastore; cp -r /data/local/tmp/sc/$1${DATA_SUFFIX} /data/data/com.superchant/files/datastore; chown -R \$U:\$U /data/data/com.superchant/files/datastore; chmod 700 /data/data/com.superchant/files/datastore; chmod 600 /data/data/com.superchant/files/datastore/*; C=\$(stat -c %C /data/data/com.superchant/files); chcon -R \$C /data/data/com.superchant/files/datastore"
