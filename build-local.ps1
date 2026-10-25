param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments = @('build')
)

$ErrorActionPreference = 'Stop'
$javaExecutable = Join-Path $PSScriptRoot '.tooling/jdk-25.0.4.1+1/bin/java.exe'
$gradleLauncher = Join-Path $PSScriptRoot '.tooling/gradle-9.6.0/lib/gradle-launcher-9.6.0.jar'
if (!(Test-Path -LiteralPath $javaExecutable) -or !(Test-Path -LiteralPath $gradleLauncher)) {
    throw 'Project-local tooling is unavailable. Use Java 25 with gradlew.bat build instead.'
}

# The host TEMP directory cannot handle Java's local socket files. Keep all
# temporary files and Gradle caches inside this project for the local build.
$socketTemp = Join-Path $PSScriptRoot '.tooling/socket-temp'
$buildTemp = Join-Path $PSScriptRoot '.t'
New-Item -ItemType Directory -Path $socketTemp, $buildTemp -Force | Out-Null
$previousTemp = $env:TEMP
$previousTmp = $env:TMP
$previousJava = $env:JAVA_HOME
Push-Location $PSScriptRoot
try {
    $env:TEMP = $buildTemp
    $env:TMP = $buildTemp
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExecutable)
    & $javaExecutable '-Xmx3G' `
        '-Dorg.gradle.daemon=false' '-Dorg.gradle.jvmargs=-Xmx3G' `
        '-Dorg.gradle.internal.instrumentation.agent=false' `
        "-Djdk.net.unixdomain.tmpdir=$socketTemp" "-Djava.io.tmpdir=$buildTemp" `
        '-cp' $gradleLauncher 'org.gradle.launcher.GradleMain' `
        '--gradle-user-home' (Join-Path $PSScriptRoot '.gradle-user-home') `
        '--no-daemon' @GradleArguments
    $buildExit = $LASTEXITCODE
} finally {
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
    $env:JAVA_HOME = $previousJava
    Pop-Location
}
exit $buildExit
