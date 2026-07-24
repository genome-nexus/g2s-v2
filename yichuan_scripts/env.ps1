# Source before build/run: . .\yichuan_scripts\env.ps1
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

# Prefer Corretto 8, fallback to existing JDK 8
$jdk8 = @(
    "C:\Program Files\Amazon Corretto\jdk1.8.0_492",
    "C:\Program Files\Java\jdk1.8.0_202"
) | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1

if ($jdk8) {
    $env:JAVA_HOME = $jdk8
    $env:Path = "$env:JAVA_HOME\bin;" + ($env:Path -split ';' | Where-Object { $_ -notmatch 'jdk-11|Microsoft\\jdk' }) -join ';'
}

$mavenHome = Join-Path $Root "tools\apache-maven-3.9.6"
if (Test-Path "$mavenHome\bin\mvn.cmd") {
    $env:MAVEN_HOME = $mavenHome
    $env:Path = "$mavenHome\bin;$env:Path"
}

$dockerBin = "C:\Program Files\Docker\Docker\resources\bin"
if (Test-Path $dockerBin) {
    $env:Path = "$dockerBin;$env:Path"
}

Write-Host "JAVA_HOME=$env:JAVA_HOME"
# mvn -version writes to stderr; under $ErrorActionPreference='Stop' (e.g. when this is
# dot-sourced by start-services.ps1) that stderr is turned into a terminating
# NativeCommandError that would abort the caller. Force 'Continue' just for these prints.
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
if (Get-Command mvn -ErrorAction SilentlyContinue) { mvn -version 2>&1 | Select-Object -First 1 }
$ErrorActionPreference = $prevEAP
