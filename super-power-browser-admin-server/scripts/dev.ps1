[CmdletBinding()]
param(
    [switch]$Build,
    [switch]$Stop,
    [int]$Port = 0
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $serverRoot '.dev'
$envFile = Join-Path $serverRoot '.env'

function Read-ServerEnv {
    if (-not (Test-Path -LiteralPath $envFile)) {
        Write-Warning "File $envFile was not found; application.yml defaults will be used."
        return
    }

    $allowed = @(
        'DB_URL', 'DB_USER', 'DB_PASSWORD', 'OPS_PORT', 'OPS_ADMIN_USERNAME', 'OPS_ADMIN_PASSWORD',
        'APP_DATA_KEY', 'KERNEL_S3_BUCKET', 'KERNEL_S3_ENDPOINT', 'KERNEL_S3_REGION',
        'KERNEL_S3_PREFIX', 'KERNEL_S3_PATH_STYLE', 'KERNEL_S3_ALLOW_HTTP', 'KERNEL_URL_MINUTES'
    )
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#') -or -not $trimmed.Contains('=')) {
            continue
        }
        $pair = $trimmed -split '=', 2
        $name = $pair[0].Trim()
        if ($allowed -contains $name) {
            [Environment]::SetEnvironmentVariable($name, $pair[1], 'Process')
        }
    }
}

function Stop-AdminServer {
    $pidFile = Join-Path $runDirectory 'admin-server.pid'
    if (Test-Path -LiteralPath $pidFile) {
        $servicePid = 0
        [int]::TryParse((Get-Content -LiteralPath $pidFile -Raw -Encoding ASCII).Trim(), [ref]$servicePid) | Out-Null
        if ($servicePid -gt 0) {
            $process = Get-Process -Id $servicePid -ErrorAction SilentlyContinue
            if ($null -ne $process -and $process.ProcessName -eq 'java') {
                Stop-Process -Id $servicePid -Force -ErrorAction SilentlyContinue
            }
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
}

Set-Location -LiteralPath $serverRoot
Read-ServerEnv
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

# Windows PowerShell can expose both PATH and Path. Start-Process with
# redirected output rejects the duplicate case variants.
$pathValue = [Environment]::GetEnvironmentVariable('PATH', 'Process')
if ($pathValue) {
    [Environment]::SetEnvironmentVariable('Path', $null, 'Process')
    [Environment]::SetEnvironmentVariable('PATH', $pathValue, 'Process')
}

if ($Stop) {
    Stop-AdminServer
    Write-Output 'Admin server stopped.'
    exit 0
}

$javaCommand = Get-Command java -ErrorAction Stop
$jar = Join-Path $serverRoot 'target\super-power-browser-admin-server-0.1.0.jar'

if ($Build -or -not (Test-Path -LiteralPath $jar)) {
    # Maven's Spring Boot repackage step replaces the jar in place. Stop the
    # previous JVM first so Windows does not keep the jar locked.
    Stop-AdminServer
    $mavenCommand = Get-Command mvn -ErrorAction Stop
    $javaInfo = (& cmd.exe /d /c "`"$($javaCommand.Source)`" -version 2>&1" | Out-String)
    $buildArguments = @('-DskipTests', 'package')
    $javaVersion = [regex]::Match($javaInfo, 'version \D*(\d+)').Groups[1].Value
    if ($javaVersion -and [int]$javaVersion -lt 21) {
        $buildArguments = @("-Djava.version=$javaVersion") + $buildArguments
        Write-Warning "JDK $javaVersion detected; building with -Djava.version=$javaVersion."
    }
    Write-Host 'Building admin server ...'
    & $mavenCommand.Source @buildArguments | ForEach-Object { Write-Host $_ }
    $buildExitCode = $LASTEXITCODE
    if ($buildExitCode -ne 0) {
        throw "Maven build failed with exit code $buildExitCode."
    }
}

if (-not (Test-Path -LiteralPath $jar)) {
    throw "Missing $jar; run with -Build to build the server first."
}

if ($Port -ne 0) {
    if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Port must be between 1024 and 65535.' }
    $env:OPS_PORT = $Port.ToString()
}
$port = if ($env:OPS_PORT) { [int]$env:OPS_PORT } else { 18082 }

Stop-AdminServer
$stdout = Join-Path $runDirectory 'admin-server.log'
$stderr = Join-Path $runDirectory 'admin-server-error.log'
$process = Start-Process -FilePath $javaCommand.Source -ArgumentList @('-jar', $jar) -WorkingDirectory $serverRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$process.Id | Set-Content -LiteralPath (Join-Path $runDirectory 'admin-server.pid') -Encoding ASCII

$ready = $false
for ($attempt = 0; $attempt -lt 45; $attempt++) {
    if ($process.HasExited) {
        throw "Admin server exited during startup; inspect $stderr."
    }
    try {
        $health = Invoke-RestMethod "http://127.0.0.1:$port/api/admin/health" -TimeoutSec 1
        if ($health.service -eq 'operations') { $ready = $true; break }
    } catch {}
    Start-Sleep -Seconds 1
}
if (-not $ready) {
    throw "Admin server startup timed out; inspect $stdout and $stderr."
}

[pscustomobject]@{
    Api = "http://127.0.0.1:$port"
    ApiPid = [int](Get-Content -LiteralPath (Join-Path $runDirectory 'admin-server.pid') -Raw -Encoding ASCII)
    Logs = $runDirectory
} | ConvertTo-Json
