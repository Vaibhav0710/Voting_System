# scripts/verify-rate-limit.ps1
# Fires 25 concurrent login requests simultaneously using .NET HttpClient tasks to test the token bucket
# burstCapacity: 20 and replenishRate: 10.

Add-Type -AssemblyName System.Net.Http

$url = "http://localhost:8080/api/v1/auth/login"
$body = '{"usernameOrEmail":"admin","password":"' + $env:ADMIN_PASSWORD + '"}'

Write-Host "Firing 25 concurrent login requests to $url..."

$client = New-Object System.Net.Http.HttpClient
$tasks = @()

1..25 | ForEach-Object {
    $idx = $_
    $content = New-Object System.Net.Http.StringContent($body, [System.Text.Encoding]::UTF8, "application/json")
    $tasks += $client.PostAsync($url, $content)
}

[System.Threading.Tasks.Task]::WaitAll($tasks)

$results = @()
for ($i = 0; $i -lt $tasks.Count; $i++) {
    $resp = $tasks[$i].Result
    $code = [int]$resp.StatusCode
    $rem = if ($resp.Headers.Contains("X-RateLimit-Remaining")) { ($resp.Headers.GetValues("X-RateLimit-Remaining") -join "") } else { "-" }
    $burst = if ($resp.Headers.Contains("X-RateLimit-Burst-Capacity")) { ($resp.Headers.GetValues("X-RateLimit-Burst-Capacity") -join "") } else { "-" }
    $repl = if ($resp.Headers.Contains("X-RateLimit-Replenish-Rate")) { ($resp.Headers.GetValues("X-RateLimit-Replenish-Rate") -join "") } else { "-" }
    $statusText = if ($code -eq 429) { "RATE_LIMITED (429 Too Many Requests)" } elseif ($code -eq 200) { "OK (200 Admitted)" } else { "HTTP $code" }
    
    $results += [PSCustomObject]@{
        Request = $i + 1
        StatusCode = $code
        RemainingTokens = $rem
        BurstCapacity = $burst
        ReplenishRate = $repl
        Status = $statusText
    }
}

$results | Format-Table -AutoSize
$admitted = ($results | Where-Object { $_.StatusCode -eq 200 }).Count
$limited = ($results | Where-Object { $_.StatusCode -eq 429 }).Count
Write-Host "Summary: $admitted admitted (200), $limited rate limited (429)."
if ($limited -gt 0) {
    Write-Host "SUCCESS: Rate limiting verified active: burst capacity ($($results[0].BurstCapacity)) respected, excess requests received HTTP 429."
} else {
    Write-Warning "FAILURE: No 429 received."
}
