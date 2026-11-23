@echo off
setlocal
rem Manual local Gradle launcher for a full GitHub checkout (not a scheduled task).
set "APP_HOME=%~dp0"
set "WRAPPER=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if not exist "%WRAPPER%" (
  >&2 echo Missing gradle-wrapper.jar. Use a full, verified GitHub checkout.
  exit /b 1
)
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)
"%JAVA_EXE%" -Xmx64m -Dorg.gradle.appname=gradlew -classpath "%WRAPPER%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
