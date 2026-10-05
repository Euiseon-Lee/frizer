param(
    [Parameter(Mandatory=$true)][string]$EnvironmentFile,
    [Parameter(Mandatory=$true)][string]$LoginId,
    [ValidateSet('USER','ADMIN')][string]$Role = 'USER',
    [switch]$Local,
    [string]$Jar = "$PSScriptRoot/../build/libs/frizer-0.0.1.jar"
)
$ErrorActionPreference = 'Stop'
$configFile = (Resolve-Path -LiteralPath $EnvironmentFile).Path
$jarFile = (Resolve-Path -LiteralPath $Jar).Path
$javaCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$configuration = @{}
# Generated dotenv values are JSON quoted; Java properties retains those quotes.
foreach ($line in [IO.File]::ReadAllLines($configFile)) {
    if ($line -match '^\s*(#|$)') { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw '환경 파일 형식을 확인해 주세요.' }
    $key = $Matches[1]
    $value = $Matches[2].Trim()
    if ($key -notin @('FRIZER_DB_URL','FRIZER_DB_USERNAME','FRIZER_DB_PASSWORD','FRIZER_DB_PORT','FRIZER_LOGIN_USERNAME','FRIZER_LOGIN_PASSWORD')) { continue }
    if ($value.StartsWith('"')) { $value = ConvertFrom-Json -InputObject $value }
    elseif ($value.StartsWith("'") -and $value.EndsWith("'")) { $value = $value.Substring(1, $value.Length - 2) }
    $configuration[$key] = [string]$value
}
if ($Local) {
    $localDbPort = if ($configuration.ContainsKey('FRIZER_DB_PORT')) { [int]$configuration['FRIZER_DB_PORT'] } else { 5432 }
    if ($localDbPort -lt 1 -or $localDbPort -gt 65535) { throw '로컬 DB 포트가 올바르지 않습니다.' }
    # Local mode always pins the host/database/user; it never consumes a remote URL.
    $configuration['FRIZER_DB_URL'] = "jdbc:postgresql://127.0.0.1:$localDbPort/frizer"
    $configuration['FRIZER_DB_USERNAME'] = 'frizer'
}
foreach ($key in @('FRIZER_DB_URL','FRIZER_DB_USERNAME','FRIZER_DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace($configuration[$key])) { throw "환경 파일에 $key 설정이 필요합니다." }
}
Write-Host "새 계정: $LoginId / 권한: $Role / 로컬 모드: $Local"
$secret = Read-Host '새 사용자 비밀번호 (10자 이상)' -AsSecureString
$confirm = Read-Host '비밀번호 확인' -AsSecureString
$secretPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
$confirmPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirm)
$savedEnvironment = @{}
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPtr)
    if ($password -cne [Runtime.InteropServices.Marshal]::PtrToStringBSTR($confirmPtr)) { throw '비밀번호가 일치하지 않습니다.' }
    foreach ($key in @($configuration.Keys) + @('FRIZER_NEW_LOGIN_ID','FRIZER_NEW_PASSWORD','FRIZER_NEW_ROLE')) {
        $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    }
    foreach ($key in $configuration.Keys) { [Environment]::SetEnvironmentVariable($key, $configuration[$key], 'Process') }
    $env:FRIZER_NEW_LOGIN_ID = $LoginId
    $env:FRIZER_NEW_PASSWORD = $password
    $env:FRIZER_NEW_ROLE = $Role
    # Passwords are passed through the process environment, never command-line arguments.
    & $javaCommand -jar $jarFile '--spring.profiles.active=prod' '--spring.main.web-application-type=none' `
        '--frizer.account-provision.enabled=true' '--spring.flyway.enabled=false'
    if ($LASTEXITCODE -ne 0) { throw '사용자 등록에 실패했습니다. 기존 계정은 덮어쓰지 않습니다.' }
    Write-Host "$Role 계정을 등록했습니다: $LoginId"
} finally {
    foreach ($key in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key], 'Process') }
    $password = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPtr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($confirmPtr)
    $secret.Dispose()
    $confirm.Dispose()
}
