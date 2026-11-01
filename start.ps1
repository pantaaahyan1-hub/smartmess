$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Test-Path 'C:\Program Files\Java\jdk-21\bin\java.exe') { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' }
if (Test-Path "$env:USERPROFILE\.jdks\corretto-21.0.3\bin\java.exe") { $env:JAVA_HOME = "$env:USERPROFILE\.jdks\corretto-21.0.3" }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw 'Install JDK 17+ and set JAVA_HOME to its folder. See README.md.' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$jar=Join-Path $PSScriptRoot 'target\smart-mess-1.0.0.jar'
if (!(Test-Path -LiteralPath $jar)) {
    & "$PSScriptRoot\tools\apache-maven-3.9.9\bin\mvn.cmd" -B -ntp verify
    if ($LASTEXITCODE -ne 0) { throw 'Build failed. Review the Maven output above.' }
}
& "$env:JAVA_HOME\bin\java.exe" '-Djdk.net.unixdomain.tmpdir=C:\smartmess-no-unix-sockets\unavailable' -jar $jar
exit $LASTEXITCODE
