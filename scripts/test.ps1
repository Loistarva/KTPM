param([switch]$PortablePostgres)
$ErrorActionPreference="Stop"
$repoRoot=Split-Path -Parent $PSScriptRoot
$mavenArgs=@("-f",(Join-Path $repoRoot "backend/pom.xml"),"clean","verify")
if($PortablePostgres){$mavenArgs+="-Dktpm.test.embedded=true"}
& (Join-Path $repoRoot "backend/mvnw.cmd") @mavenArgs
if($LASTEXITCODE-ne 0){throw "Test/build failed with exit code $LASTEXITCODE"}

