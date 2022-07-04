#!/bin/bash

for FILE in output/*.apk; do
  sign.sh "$FILE" key "android"
done
