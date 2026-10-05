param([int]$BackendPort=18080,[int]$DatabasePort=15432,[int]$FrontendPort=15173)
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent $PSScriptRoot
if(-not (Get-Command docker -ErrorAction SilentlyContinue)){throw 'Docker CLI is missing. Install/start Docker Desktop before running this smoke test.'}
& docker info --format '{{.ServerVersion}}'
if($LASTEXITCODE-ne 0){throw 'Docker engine is not available'}
$savedPort=$env:PORT
$savedDbPort=$env:DB_PORT
$savedFrontendPort=$env:FRONTEND_PORT
$projectName='ktpm-p1-smoke'
$composeFile=Join-Path $repoRoot 'docker-compose.yml'
try {
 $env:PORT=[string]$BackendPort
 $env:DB_PORT=[string]$DatabasePort
 $env:FRONTEND_PORT=[string]$FrontendPort
 & docker compose -p $projectName -f $composeFile config --quiet
 if($LASTEXITCODE-ne 0){throw 'Compose configuration is invalid'}
 & docker compose -p $projectName -f $composeFile up --build -d
 if($LASTEXITCODE-ne 0){throw 'Docker build/start failed'}
 $base="http://localhost:$BackendPort"
 $ready=$false
 for($attempt=0;$attempt-lt 90;$attempt++){
  try {if((Invoke-RestMethod "$base/actuator/health" -TimeoutSec 3).status-eq 'UP'){$ready=$true;break}}catch{}
  Start-Sleep -Seconds 2
 }
 if(-not $ready){throw 'Backend health did not become UP'}
 $front="http://localhost:$FrontendPort"
 $ready=$false
 for($attempt=0;$attempt-lt 30;$attempt++){
  try {
   $html=(Invoke-WebRequest "$front/login" -UseBasicParsing -TimeoutSec 3).Content
   if($html -match 'id="root"' -and (Invoke-RestMethod "$front/api/auctions" -TimeoutSec 3)){$ready=$true;break}
  }catch{}
  Start-Sleep -Seconds 2
 }
 if(-not $ready){throw 'Frontend SPA/API proxy did not become ready'}
 # Run the API checks through Nginx, including Authorization forwarding.
 $base=$front
 $suffix=[Guid]::NewGuid().ToString('N').Substring(0,12)
 $account=@{username="smoke$suffix";email="smoke$suffix@example.com";password='DockerTest123!'}
 $null=Invoke-RestMethod "$base/api/auth/register" -Method Post -ContentType application/json -Body ($account|ConvertTo-Json)
 $login=Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType application/json -Body (@{login=$account.username;password=$account.password}|ConvertTo-Json)
 $headers=@{Authorization="Bearer $($login.accessToken)"}
 $me=Invoke-RestMethod "$base/api/auth/me" -Headers $headers
 if($me.username-ne $account.username){throw 'Protected GET contract mismatch'}
 $now=[DateTimeOffset]::UtcNow
 $body=@{name='Docker smoke';description='Real Docker backend and PostgreSQL';condition='NEW';imageUrl=$null;startingPrice='100';minimumBidStep='10';startingTime=$now.AddSeconds(-1).ToString('o');endingTime=$now.AddMinutes(10).ToString('o')}
 $auction=Invoke-RestMethod "$base/api/auctions" -Method Post -Headers $headers -ContentType application/json -Body ($body|ConvertTo-Json)
 $loaded=Invoke-RestMethod "$base/api/auctions/$($auction.id)"
 if($loaded.name-ne 'Docker smoke'){throw 'Auction JSON contract mismatch'}
 $null=Invoke-RestMethod "$base/api/auctions/$($auction.id)" -Method Delete -Headers $headers
 $null=Invoke-RestMethod "http://localhost:$BackendPort/v3/api-docs"
 'PASS: Docker frontend SPA/API proxy, backend, PostgreSQL, login, protected GET/POST, DELETE and OpenAPI.'
}finally{
 # Only this named smoke project's resources and test volume are removed.
 & docker compose -p $projectName -f $composeFile down --volumes
 $env:PORT=$savedPort
 $env:DB_PORT=$savedDbPort
 $env:FRONTEND_PORT=$savedFrontendPort
}
