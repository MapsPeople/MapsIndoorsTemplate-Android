# MapsIndoorsTemplate-Android

You can read how to implement this project into your own app here: [Android - MapsIndoors template](https://docs.mapsindoors.com/content/getting-started/android/mapsindoors-template/)

Remember to add the lifecycle calls to stop the positioning when the app is in the background. You can see how at line 399-417 inside MapsFragment.kt

Along with the guide also remember to add the positioning directory with the position provider into your code. You must handle the permissions yourself, a small sample is shown inside MainActivity at line 32 and 35-50.
