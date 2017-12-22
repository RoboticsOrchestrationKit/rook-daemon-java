#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
./mvn_package.sh
cp -r $DIR/../target $DIR/target
cd $DIR
docker build -t rook_daemon .
rm -r $DIR/target