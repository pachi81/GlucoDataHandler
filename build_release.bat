@echo off

rem Step 1: Increment version code in build.gradle
rem echo Incrementing version code in build.gradle...
rem powershell -Command "$file='build.gradle'; (Get-Content $file) | ForEach-Object { if ($_ -match 'project\.ext\.set\(\"versionCode\",\s*(\d+)\)') { $v = [int]$matches[1] + 1; 'project.ext.set(\"versionCode\", ' + $v + ')' } else { $_ } } | Set-Content $file"

rem Step 2: Sync project (trigger configuration phase)
rem echo Syncing project...
rem call gradlew.bat help

rem Step 3: Build and explicitly trigger copy tasks from afterEvaluate
echo Starting build process for Release (AABs, APKs and Second APKs)...
call gradlew.bat clean :mobile:bundleRelease :wear:bundleRelease :mobile:assembleRelease :wear:assembleRelease :mobile:assembleSecond :wear:assembleSecond
 rem :mobile:copyAndroidBundlePostBuild :wear:copyAndroidBundlePostBuild :mobile:copyAndroidApksPostBuild :wear:copyAndroidApksPostBuild :mobile:copyAndroidSecondApksPostBuild :wear:copyAndroidSecondApksPostBuild

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build and Copy completed successfully!
) else (
    echo.
    echo ERROR during build process!
)
pause