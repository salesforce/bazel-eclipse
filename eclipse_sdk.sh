#!/bin/bash

# when developing the feature, it is useful to be explicit about which Eclipse SDK instance you are launching

set -e -x

# create a my_env.sh and set $ECLIPSE_DIR
source my_env.sh

# Mac path, update if you are on another platform
$ECLIPSE_DIR/Eclipse.app/Contents/MacOS/eclipse &
