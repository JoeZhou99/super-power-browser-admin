param([int]$Port=3200,[string]$ApiUrl='http://127.0.0.1:18082')
$ErrorActionPreference='Stop'
$projectRoot=Join-Path $PSScriptRoot 'super-power-browser-admin-web'
$runtime=Join-Path $PSScriptRoot '.local-preview'
$vite=Join-Path $projectRoot 'node_modules\vite\bin\vite.js'
# Reuse dependencies from the previous flat layout until npm ci is run in web/.
if(-not (Test-Path -LiteralPath $vite)){$vite=Join-Path $PSScriptRoot 'node_modules\vite\bin\vite.js'}
if(-not (Test-Path -LiteralPath $vite)){throw 'Run npm ci in super-power-browser-admin-web first.'}
if(-not (Test-Path -LiteralPath (Join-Path $projectRoot 'dist\index.html'))){throw 'Run npm run build in super-power-browser-admin-web first.'}
if($Port -lt 1024 -or $Port -gt 65535){throw 'Port must be between 1024 and 65535.'}
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$pidFile=Join-Path $runtime 'web.pid'
$config=Join-Path $projectRoot 'vite.config.ts'
$listener=Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if($listener){
    $existing=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if(-not $existing -or $existing.Name -ne 'node.exe' -or -not $existing.CommandLine.Contains($config)){throw "Port $Port belongs to another preview. Stop it or specify a different -Port."}
    Write-Output "Operations console already running: http://127.0.0.1:$Port"
    exit 0
}
$env:VITE_API_PROXY_TARGET=$ApiUrl
$process=Start-Process -FilePath (Get-Command node).Source -ArgumentList @("`"$vite`"",'preview','--config',"`"$config`"",'--host','127.0.0.1','--port',$Port,'--strictPort') -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'web.log') -RedirectStandardError (Join-Path $runtime 'web-error.log') -PassThru
$process.Id|Set-Content -LiteralPath $pidFile -Encoding ASCII
for($attempt=0;$attempt -lt 30;$attempt++){
    if($process.HasExited){throw 'Operations preview exited. Inspect .local-preview/web-error.log.'}
    try{$response=Invoke-WebRequest "http://127.0.0.1:$Port/login" -UseBasicParsing -TimeoutSec 1;if($response.StatusCode -eq 200){Write-Output "Operations console: http://127.0.0.1:$Port`nAPI: $ApiUrl";exit 0}}catch{}
    Start-Sleep -Milliseconds 500
}
throw 'Operations preview startup timed out. Inspect .local-preview/web.log.'
