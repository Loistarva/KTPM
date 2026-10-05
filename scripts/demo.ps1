param(
 [string]$BaseUrl="http://localhost:8080",
 [ValidateRange(20,600)][int]$DurationSeconds=60,
 [switch]$WaitForEnd
)
$ErrorActionPreference="Stop"
function Api([string]$Method,[string]$Path,$Body,[string]$Token) {
 $headers=@{}; if($Token){$headers.Authorization="Bearer $Token"}
 $args=@{Method=$Method;Uri="$BaseUrl$Path";Headers=$headers;TimeoutSec=30}
 if($null-ne $Body){$args.ContentType="application/json";$args.Body=ConvertTo-Json -InputObject $Body -Depth 8}
 Invoke-RestMethod @args
}
function Login([string]$Name,[string]$Password) { (Api POST "/api/auth/login" @{login=$Name;password=$Password} "").accessToken }
$sellerToken=Login "seller" "Seller123!"
$firstToken=Login "bidder1" "Bidder123!"
$secondToken=Login "bidder2" "Bidder123!"
$now=[DateTimeOffset]::UtcNow
$auction=Api POST "/api/auctions" @{name="KTPM demo $(Get-Date -Format HHmmss)";description="Demo manual auction";condition="NEW";imageUrl=$null;startingPrice=100000;minimumBidStep=10000;startingTime=$now.AddSeconds(-1).ToString("o");endingTime=$now.AddSeconds($DurationSeconds).ToString("o")} $sellerToken
$id=$auction.id
$null=Api POST "/api/auctions/$id/bids" @{amount=100000} $firstToken
$null=Api POST "/api/auctions/$id/bids" @{amount=200000} $secondToken
$null=Api POST "/api/auctions/$id/bids" @{amount=210000} $firstToken
$auction=Api GET "/api/auctions/$id" $null ""
if($auction.currentPrice-ne 210000){throw "Unexpected bid price: $($auction.currentPrice)"}
Write-Output "Auction ID: $id - current price: $($auction.currentPrice)"
Write-Output "Open the auction page and click Refresh to view current server state."
Write-Output "Ends at $($auction.endingTime). Bidder1 leads."
if($WaitForEnd){
 $deadline=[DateTimeOffset]::UtcNow.AddSeconds($DurationSeconds+15)
 do {
  Start-Sleep -Seconds 2
  $auction=Api GET "/api/auctions/$id" $null ""
 } while($auction.status-eq "ACTIVE" -and [DateTimeOffset]::UtcNow-lt $deadline)
 if($auction.status-ne "ENDED"){throw "Unexpected final state: $($auction.status)"}
 $winner=Api GET "/api/auth/me" $null $firstToken
 if($auction.finalPrice-ne 210000 -or $auction.winnerUserId-ne $winner.id){throw "Unexpected final winner or price"}
 Write-Output "SUCCESS: auction ENDED, winner and final price verified."
 $auction | Format-List
}
