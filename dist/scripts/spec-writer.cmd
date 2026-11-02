@echo off
rem Lance spec-writer depuis le jar construit (mvnw.cmd -B package -DskipTests), avec un JDK 25 local.
rem Reserve aux developpeurs : l'installeur embarque son propre runtime Java.
rem Usage : dist\scripts\spec-writer.cmd [options Java -D...]
rem   SPEC_WRITER_JAR : chemin du jar (par defaut backend\target\quarkus-app\quarkus-run.jar)
setlocal EnableDelayedExpansion

set "ROOT=%~dp0..\.."
if defined SPEC_WRITER_JAR (set "JAR=%SPEC_WRITER_JAR%") else (set "JAR=%ROOT%\backend\target\quarkus-app\quarkus-run.jar")
if defined JAVA_HOME (set "JAVA=%JAVA_HOME%\bin\java.exe") else (set "JAVA=java")

set "VERSION="
for /f "tokens=3" %%v in ('"!JAVA!" -version 2^>^&1 ^| findstr /i "version"') do if not defined VERSION set "VERSION=%%~v"
if not defined VERSION (
  echo Java introuvable : installez un JDK 25 et definissez JAVA_HOME. 1>&2
  exit /b 1
)
for /f "delims=." %%m in ("!VERSION!") do set "MAJOR=%%m"
if !MAJOR! LSS 25 (
  echo Java 25 ou plus est requis ^(trouve : !VERSION!^). 1>&2
  exit /b 1
)
if not exist "%JAR%" (
  echo Jar absent ^(%JAR%^) : lancez d'abord mvnw.cmd -B package -DskipTests 1>&2
  exit /b 1
)

"%JAVA%" %* -jar "%JAR%"
