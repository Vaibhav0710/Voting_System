# scripts/sample-load.ps1
# Samples the compose stack while a load test runs (see docs/plans/2026-10-26-10k-load-run.md):
#   cpu            CPU % of every container of this compose project (docker stats; 100 = one core)
#   pg_lock_waits  Postgres sessions waiting on a lock, i.e. votes queued on an election's chain-head row
# Appends one row per value to a CSV (time,kind,name,value) and prints each series' maximum at the end.
# Run from the repository root while the stack is up, for example:
#   .\scripts\sample-load.ps1 -DurationSeconds 150 -OutFile load-tests\target\r2-sample.csv

param(
    [Parameter(Mandatory = $true)][int]$DurationSeconds,
    [Parameter(Mandatory = $true)][string]$OutFile,
    [int]$IntervalSeconds = 5
)

$ErrorActionPreference = 'Stop'

# Sent on stdin and run as the container's own POSTGRES_USER, so no credential is read or passed here.
$LockWaitsQuery = "select count(*) from pg_stat_activity where wait_event_type = 'Lock';"

function Get-ContainerCpu([string[]]$ContainerIds) {
    $lines = docker stats --no-stream --format '{{.Name}};{{.CPUPerc}}' $ContainerIds
    if ($LASTEXITCODE -ne 0) { throw 'docker stats failed: is Docker Desktop running?' }
    $lines | ForEach-Object {
        $name, $cpu = $_ -split ';', 2
        [PSCustomObject]@{ kind = 'cpu'; name = $name; value = [double]$cpu.TrimEnd('%') }
    }
}

function Get-LockWaits {
    $count = $LockWaitsQuery | docker compose exec -T postgres sh -c 'psql -U $POSTGRES_USER -d voting_system -tA'
    if ($LASTEXITCODE -ne 0) { throw 'Could not query Postgres: is the postgres container up?' }
    [PSCustomObject]@{ kind = 'pg_lock_waits'; name = 'voting_system'; value = [int]"$count".Trim() }
}

function Write-Sample([string]$Time, $Rows) {
    foreach ($row in $Rows) {
        $value = $row.value.ToString([cultureinfo]::InvariantCulture)
        Add-Content -Path $OutFile -Value "$Time,$($row.kind),$($row.name),$value" -Encoding utf8
    }
}

function Show-Maxima {
    Import-Csv $OutFile | Group-Object kind, name | ForEach-Object {
        [PSCustomObject]@{
            Kind = $_.Group[0].kind
            Name = $_.Group[0].name
            Max  = ($_.Group | ForEach-Object { [double]$_.value } | Measure-Object -Maximum).Maximum
        }
    } | Sort-Object Kind, @{ Expression = 'Max'; Descending = $true } | Format-Table -AutoSize
}

$containerIds = docker compose ps -q
if ($LASTEXITCODE -ne 0 -or -not $containerIds) {
    throw 'No running compose containers: start the stack first (docker compose up -d).'
}

$outDir = Split-Path -Parent $OutFile
if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
Set-Content -Path $OutFile -Value 'time,kind,name,value' -Encoding utf8

$end = (Get-Date).AddSeconds($DurationSeconds)
while ((Get-Date) -lt $end) {
    $tick = Get-Date
    Write-Sample $tick.ToString('HH:mm:ss') (@(Get-ContainerCpu $containerIds) + (Get-LockWaits))
    $waitMs = [int](($IntervalSeconds - ((Get-Date) - $tick).TotalSeconds) * 1000)
    if ($waitMs -gt 0) { Start-Sleep -Milliseconds $waitMs }
}

Write-Host "Samples written to $OutFile"
Show-Maxima
