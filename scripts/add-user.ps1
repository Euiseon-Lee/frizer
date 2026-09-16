param(
    [Parameter(Mandatory=$true)][string]$EnvironmentFile,
    [Parameter(Mandatory=$true)][string]$LoginId,
    [string]$Jar = "$PSScriptRoot/../build/libs/frizer-0.0.1.jar"
)
$ErrorActionPreference = 'Stop'
# An explicit environment file selects the target. Never print its contents or rewrite it.
$configFile = (Resolve-Path -LiteralPath $EnvironmentFile).Path.Replace('\','/')
$jarFile = (Resolve-Path -LiteralPath $Jar).Path
$javaCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$secret = Read-Host '새 사용자 비밀번호 (10자 이상)' -AsSecureString
$confirm = Read-Host '비밀번호 확인' -AsSecureString
$secretPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
$confirmPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirm)
$oldLogin = $env:FRIZER_NEW_LOGIN_ID
$oldPassword = $env:FRIZER_NEW_PASSWORD
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPtr)
    if ($password -cne [Runtime.InteropServices.Marshal]::PtrToStringBSTR($confirmPtr)) { throw '비밀번호가 일치하지 않습니다.' }
    $env:FRIZER_NEW_LOGIN_ID = $LoginId
    $env:FRIZER_NEW_PASSWORD = $password
    # Secrets are process environment values, never command-line arguments or shell history.
    & $javaCommand -jar $jarFile '--spring.profiles.active=prod' '--spring.main.web-application-type=none' `
        "--spring.config.import=file:$configFile[.properties]" '--frizer.account-provision.enabled=true' '--spring.flyway.enabled=false'
    if ($LASTEXITCODE -ne 0) { throw '사용자 등록에 실패했습니다. 기존 계정은 덮어쓰지 않습니다.' }
    Write-Host '일반 사용자 계정을 등록했습니다.'
} finally {
    $env:FRIZER_NEW_LOGIN_ID = $oldLogin
    $env:FRIZER_NEW_PASSWORD = $oldPassword
    $password = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPtr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($confirmPtr)
    $secret.Dispose()
    $confirm.Dispose()
}
