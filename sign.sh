#!/bin/bash
#
# Author: Jordan Doyle
#
# Required Parameters:
#   - Input APK File : The apk file on which to run DroidGraph2.0.
#   - Key Store File : The key store file used to sign the apk file. 
# 

function exit_cleanup {
	[[ -f "$temp_file" ]] && rm "$temp_file"
	temp_file_idsig="temp_file.apk.idsig"
    [[ -f "$temp_file_idsig" ]] && rm "$temp_file_idsig"
}

if [ $# -ne 2 ]; then
    echo "$0 Error: Required parameters not specified."
    exit 2
fi

if [[ ! -d "$ANDROID_HOME" ]]; then
	echo "$0 Error: Android home environment variable is not set."
	exit 3
fi

tool_versions=($ANDROID_HOME/build-tools/*/)
build_tools=$(printf "%s\n" ${tool_versions[*]} | sort -nr | head -n1)
if [[ ! -d "$build_tools" ]]; then
	echo "$0 Error: Android build tools directory ("$build_tools") does not exist."
	exit 4
fi

zip_align="$build_tools/zipalign"
apk_signer="$build_tools/apksigner"
if [[ ! -f "$zip_align" ]] || [[ ! -f "$apk_signer" ]]; then 
	echo "$0 Error: zipalign ("$zip_align") or apksigner ("$apk_signer") cannot be found."
	exit 5
fi

if [[ ! -f "$1" ]] || [[ "$1" != *.apk ]]; then
	echo "$0 Error: Input file ("$1") does not exist or file is not an APK."
	exit 6
fi
apk_file="$1"

if [[ ! -f "$2" ]]; then
	echo "$0 Error: Input key store file ("$2") does not exist."
	exit 7
fi
key="$2"

trap exit_cleanup EXIT

temp_file="temp_file.apk"
$zip_align -f 4 "$apk_file" "$temp_file"
$apk_signer sign --ks "$key" --ks-pass pass:android "$temp_file"
cp "$temp_file" "$apk_file"
echo "$0 Info: The app is signed and located in $apk_file."
