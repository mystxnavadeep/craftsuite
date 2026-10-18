#!/bin/sh

# Gradle startup script for UN*X
# CraftSuite build wrapper

set -e

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Resolve symlinks
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi

# If wrapper jar doesn't exist, download it or invoke gradle directly
if [ ! -f "$CLASSPATH" ]; then
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    else
        mkdir -p "$APP_HOME/gradle/wrapper"
        echo "Downloading gradle-wrapper.jar..."
        curl -sLo "$CLASSPATH" "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" || true
    fi
fi

if [ -f "$CLASSPATH" ]; then
    exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
else
    echo "ERROR: gradle-wrapper.jar not found and gradle not in PATH" >&2
    exit 1
fi
