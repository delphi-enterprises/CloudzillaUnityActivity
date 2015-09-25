# Cloudzilla Unity Activity

`FcOneAppPlugin` is an Android Studio project.

## Usage Instructions
1.  The `_ExportJar` task copies the custom activity to `FcOneAppPlugin/app/release/FcOneAppActivity.jar`
2.  Copy that jar file into your Unity Project's `Plugins/Android` folder
3.  Change the main activity in your `Plugins/Android/AndroidManifest.xml` file to `com.foxcubgames.fconeappplugin.FcOneAppActivity`


## Command line build instructions

$ cd FcOneAppPlugin
$ gradle assembleDebug
$ cp .//app/build/intermediates/bundles/debug/classes.jar <Your Unity Project>/Assets/Plugins/Android/libs/FcOneAppActivity.jar