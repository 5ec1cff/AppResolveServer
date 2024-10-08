-repackageclasses
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute

-keepclasseswithmembers class io.github.a13e300.appresolveserver.MainKt {
    public static void main(java.lang.String[]);
}

-keep class io.github.a13e300.appresolveserver.PackageItem { *; }
-keep class io.github.a13e300.appresolveserver.UserItem { *; }
-keep class io.github.a13e300.appresolveserver.Request { *; }
-keep class io.github.a13e300.appresolveserver.Response { *; }
