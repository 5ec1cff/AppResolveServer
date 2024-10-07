#!/system/bin/sh
APK_PATH=$(dirname "$0")
if [ -n "$DEBUG" ]; then
  java_options="-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y -Xcompiler-option --debuggable"
else
  java_options=""
fi

CLASSNAME="io.github.a13e300.appresolveserver.MainKt"
APK_NAME="ars.apk"

exec /system/bin/app_process $java_options \
  -Djava.class.path="$APK_PATH/$APK_NAME" \
   /system/bin \
   --nice-name="AppResolveServer" \
  "$CLASSNAME" "$@"
