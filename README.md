# Droid Instrument #

Droid Instrument is a Java command line application used to instrument Android applications. The output APK is 
identical to the input APK with the addition of print statements at the beginning of each method. The added print 
statements provide data related to the instrumented method. The default data provided is the name of the method. 
Additional data provided and where it occurs is outlined below: 

* Activity onCreate method: Print the name of the Activity.
* Fragment onCreateView method: Print the name of the Fragment.
* UI listener method: Print the UI view ID.

## Build & Run ##

This is a Maven project developed in JetBrains Intellij IDE. You can clone this project and open the project in 
JetBrains Intellij IDE as a maven project, or you can clone the project and build a JAR file using the maven package 
command below: 

```
cd DroidInstrument
mvn package
```

The maven package command will build a Jar file with dependencies included. Run the project using the jar file and 
the sample APK file using the following commands: 

```
cd target
java -jar DroidInstrument-1.0-SNAPSHOT-jar-with-dependencies.jar -a "samples/activity_lifecycle_1.apk"
```

## Sign & Run APK ##

Before you can install the output APK on an Android device you need to sign the APK. Run the below commands, replacing
"app.apk" with the name of your APK file.

```
./sign.sh output/app.apk key
adb install -r -t output/app.apk
```