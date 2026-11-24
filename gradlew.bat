@echo off
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
  echo Gradle wrapper JAR not found: %CLASSPATH%
  exit /b 1
)
java -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
