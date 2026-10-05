$ErrorActionPreference="Stop"
$repoRoot=Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $repoRoot "backend")
try {
 & ".\mvnw.cmd" test-compile "org.codehaus.mojo:exec-maven-plugin:3.1.0:java" "-Dexec.mainClass=com.ktpm.LocalDemo" "-Dexec.classpathScope=test"
 if($LASTEXITCODE-ne 0){throw "Portable launcher failed"}
} finally { Pop-Location }

