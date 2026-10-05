param([string]$OutputPath='')
$ErrorActionPreference='Stop'
$repoRoot=[System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Push-Location $repoRoot
try {
 if(-not $OutputPath){$OutputPath=Join-Path $repoRoot 'backend/.local/release/KTPM-source.zip'}
 $OutputPath=[System.IO.Path]::GetFullPath($OutputPath)
 if(-not $OutputPath.StartsWith($repoRoot+[System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)){throw 'Output must be inside this project'}
 if(Test-Path -LiteralPath $OutputPath){throw 'Output ZIP already exists. Choose another -OutputPath or remove only the old ZIP.'}
 # Read the current working tree, including new source not yet committed.
 $names=@(& git -c core.quotepath=false ls-files --cached --others --exclude-standard) | Sort-Object -Unique
 if($LASTEXITCODE-ne 0){throw 'Unable to list source with Git'}
 $files=@()
 foreach($name in $names){
  if(-not (Test-Path -LiteralPath $name -PathType Leaf)){continue}
  & git check-ignore --no-index --quiet -- $name
  if($LASTEXITCODE-eq 0){continue}
  if($LASTEXITCODE-ne 1){throw "Unable to check ignore rule: $name"}
  $files+=$name
 }
 if(-not $files.Count){throw 'No source files selected'}
 [void][System.IO.Directory]::CreateDirectory((Split-Path -Parent $OutputPath))
 Add-Type -AssemblyName System.IO.Compression
 Add-Type -AssemblyName System.IO.Compression.FileSystem
 $archive=[System.IO.Compression.ZipFile]::Open($OutputPath,[System.IO.Compression.ZipArchiveMode]::Create)
 try {
  foreach($name in $files){
   [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $repoRoot $name),('KTPM/'+$name.Replace('\','/')))
  }
 }finally{$archive.Dispose()}
 Write-Output "Source ZIP: $OutputPath"
 Write-Output "Files: $($files.Count). No commit/push performed."
 Write-Output 'Source only; build/cache/database/secrets are excluded using Git ignore rules.'
}finally{Pop-Location}
