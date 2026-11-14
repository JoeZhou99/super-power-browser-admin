param([int]$Port=0)
$ErrorActionPreference='Stop'
$serverRoot=Split-Path $PSScriptRoot -Parent
$envFile=Join-Path $serverRoot '.env'
if(Test-Path -LiteralPath $envFile){
    $allowed=@('DB_URL','DB_USER','DB_PASSWORD','OPS_PORT','OPS_ADMIN_USERNAME','OPS_ADMIN_PASSWORD','APP_DATA_KEY','KERNEL_S3_BUCKET','KERNEL_S3_ENDPOINT','KERNEL_S3_REGION','KERNEL_S3_PREFIX','KERNEL_S3_PATH_STYLE','KERNEL_S3_ALLOW_HTTP','KERNEL_URL_MINUTES')
    foreach($line in Get-Content -LiteralPath $envFile -Encoding UTF8){
        if($line.Trim().StartsWith('#') -or -not $line.Contains('=')){continue}
        $pair=$line -split '=',2
        if($allowed -contains $pair[0].Trim()){
            [Environment]::SetEnvironmentVariable($pair[0].Trim(),$pair[1],'Process')
        }
    }
}
if($Port -ne 0){
    if($Port -lt 1024 -or $Port -gt 65535){throw 'Port must be between 1024 and 65535.'}
    $env:OPS_PORT=$Port.ToString()
}
$jar=Join-Path $serverRoot 'target\super-power-browser-admin-server-0.1.0.jar'
if(-not (Test-Path -LiteralPath $jar)){throw 'Run mvn package in super-power-browser-admin-server first.'}
Push-Location $serverRoot
try{
    & java -jar $jar
    exit $LASTEXITCODE
}finally{
    Pop-Location
}
