# Receivers and services are referenced from the manifest and kept by AGP's default rules.
# Keep DataStore's generated protobuf classes.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
