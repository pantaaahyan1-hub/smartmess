$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot)
$jdk=Join-Path $env:USERPROFILE '.jdks\corretto-21.0.3'
if(!(Test-Path "$jdk\bin\java.exe")){$jdk=$env:JAVA_HOME}
$files=Get-ChildItem 'src' -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& "$jdk\bin\java.exe" -jar 'tools\google-java-format.jar' --replace $files
