[CmdletBinding()]
param(
    [switch]$NoBuild,
    [switch]$Stop,
    [int]$Port = 0
)

$ErrorActionPreference = 'Stop'
$serverScript = Join-Path $PSScriptRoot 'super-power-browser-admin-server\scripts\dev.ps1'

if (-not (Test-Path -LiteralPath $serverScript)) {
    throw "Missing $serverScript"
}

if ($Stop) {
    & $serverScript -Stop
    exit $LASTEXITCODE
}

if ($Port -ne 0) {
    if ($NoBuild) { & $serverScript -Port $Port }
    else { & $serverScript -Build -Port $Port }
} elseif ($NoBuild) {
    & $serverScript
} else {
    & $serverScript -Build
}
exit $LASTEXITCODE
