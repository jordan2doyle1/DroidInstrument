# Droid Instrument #

Droid Instrument is a Java command line application used to instrument Android applications. The resulting APK is
identical to the original with the addition of a print statement at the beginning of each method. The print statement
outputs the name of the method.

## Sign and Run ##

Before you can install the output APK on an Android device you need to sign the APK. Run the below commands, replacing
"app.apk" with the name of your APK file.

```
./sign.sh output/app.apk key
adb install -r -t output/app.apk
```