param([string]$OutputPath='')
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent $PSScriptRoot
if(-not $OutputPath){$OutputPath=Join-Path $repoRoot 'backend/.local/kaggle/ktpm-p1-source.zip'}
$OutputPath=[System.IO.Path]::GetFullPath($OutputPath)
if(-not $OutputPath.StartsWith($repoRoot+[System.IO.Path]::DirectorySeparatorChar)){throw 'Export path must be inside this project'}
[void][System.IO.Directory]::CreateDirectory((Split-Path -Parent $OutputPath))
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$files=@((Join-Path $repoRoot 'backend/pom.xml'),(Join-Path $repoRoot 'backend/mvnw'),(Join-Path $repoRoot 'scripts/benchmark.py'))
$files+=@('benchmark_suite.py','benchmark_config.json','compare_benchmarks.py') | ForEach-Object { Join-Path $repoRoot ('scripts/'+$_) }
$files+=Get-ChildItem -LiteralPath (Join-Path $repoRoot 'backend/src'),(Join-Path $repoRoot 'backend/.mvn') -Recurse -File | Select-Object -ExpandProperty FullName
$archive=[System.IO.Compression.ZipFile]::Open($OutputPath,[System.IO.Compression.ZipArchiveMode]::Create)
try {
 foreach($file in $files){
  $name=$file.Substring($repoRoot.Length).TrimStart('\','/').Replace('\','/')
  [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file,$name)
 }
}finally{$archive.Dispose()}
Write-Output "Kaggle source ZIP: $OutputPath"
Write-Output 'Contains source/runner only; no database, tokens, dependency cache or .git. Delete the old ZIP before exporting again.'
