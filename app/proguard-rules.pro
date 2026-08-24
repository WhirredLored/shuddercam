# ShudderCam release keep rules.
#
# The app deliberately uses no reflection: presets serialize through
# controls/PresetCodec as plain delimited text keyed by enum NAME, and DataStore
# stores primitives and strings. That means almost nothing needs keeping — but
# the enum names ARE the serialization format, so obfuscating them would silently
# invalidate every saved preset on upgrade. Hence the rules below.

# ControlId constant names ARE the on-disk format: PresetCodec writes id.name
# into every saved preset record and reads it back by matching against
# ControlId.entries. Keeping the class name alone is not enough — the constants
# themselves must keep their names, or a saved preset silently fails to load
# after an upgrade. Verify with:
#   grep "ControlId AE_MODE" app/build/outputs/mapping/release/mapping.txt
# which must map AE_MODE to AE_MODE, not to a shortened name.
-keepclassmembers enum com.huestone.shuddercam.controls.ControlId {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# The remaining enums are matched by type rather than by name, so they only need
# the standard enum accessors intact.
-keepclassmembers enum com.huestone.shuddercam.controls.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# CameraX resolves Camera2 interop implementations internally; keep the extender
# entry points R8 cannot see being used.
-keep class androidx.camera.camera2.** { *; }
-dontwarn androidx.camera.**

# Kotlin coroutines' internal service loading.
-dontwarn kotlinx.coroutines.**

# Keep the line numbers that make a release crash report readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
