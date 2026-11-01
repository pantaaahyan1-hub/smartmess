$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot)
if (Test-Path 'C:\Program Files\Java\jdk-21\bin\java.exe') { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' }
if (Test-Path "$env:USERPROFILE\.jdks\corretto-21.0.3\bin\java.exe") { $env:JAVA_HOME = "$env:USERPROFILE\.jdks\corretto-21.0.3" }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& "$PSScriptRoot\apache-maven-3.9.9\bin\mvn.cmd" -B -ntp '-Dmaven.resolver.transport=wagon' '-Dmaven.wagon.rto=20000' clean verify
exit $LASTEXITCODE
