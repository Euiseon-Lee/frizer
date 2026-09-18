param(
    [Parameter(Mandatory=$true)][string]$EnvironmentFile,
    [string]$OutputDirectory = 'C:\dev\frizer-backups'
)
$ErrorActionPreference = 'Stop'
$configFile = (Resolve-Path -LiteralPath $EnvironmentFile).Path
$configuration = @{}
# Generated dotenv values are JSON quoted; add-user.ps1 uses the same parsing.
foreach ($line in [IO.File]::ReadAllLines($configFile)) {
    if ($line -match '^\s*(#|$)') { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw '환경 파일 형식을 확인해 주세요.' }
    $key = $Matches[1]
    $value = $Matches[2].Trim()
    if ($key -notin @('FRIZER_DB_URL','FRIZER_DB_USERNAME','FRIZER_DB_PASSWORD')) { continue }
    if ($value.StartsWith('"')) { $value = ConvertFrom-Json -InputObject $value }
    elseif ($value.StartsWith("'") -and $value.EndsWith("'")) { $value = $value.Substring(1, $value.Length - 2) }
    $configuration[$key] = [string]$value
}
foreach ($key in @('FRIZER_DB_URL','FRIZER_DB_USERNAME','FRIZER_DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace($configuration[$key])) { throw "환경 파일에 $key 설정이 필요합니다." }
}
# JDBC URL(jdbc:postgresql://host[:port]/db[?params])에서 접속 정보를 얻는다.
if ($configuration['FRIZER_DB_URL'] -notmatch '^jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?]+)') { throw 'FRIZER_DB_URL 형식을 확인해 주세요.' }
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '5432' }
$dbName = $Matches[3]

New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$fileName = "frizer-$(Get-Date -Format 'yyyyMMdd-HHmmss').dump"

# 로컬 설치 없이 운영 서버(Neon PostgreSQL 18)와 맞는 pg_dump를 쓰기 위해 Docker 이미지를 사용한다.
# 비밀번호는 명령행 인자가 아니라 컨테이너 환경변수로만 전달한다.
$savedPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    $env:PGPASSWORD = $configuration['FRIZER_DB_PASSWORD']
    docker run --rm -e PGPASSWORD -e PGSSLMODE=require -v "${OutputDirectory}:/backup" postgres:18 `
        pg_dump -h $dbHost -p $dbPort -U $configuration['FRIZER_DB_USERNAME'] -d $dbName `
        --format=custom --no-owner --no-acl -f "/backup/$fileName"
    if ($LASTEXITCODE -ne 0) { throw '백업에 실패했습니다. 접속 정보와 Docker 실행 상태를 확인해 주세요.' }
} finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $savedPassword, 'Process')
}
$backupFile = Get-Item (Join-Path $OutputDirectory $fileName)
if ($backupFile.Length -lt 1kb) { throw "백업 파일이 비정상적으로 작습니다: $($backupFile.Length) bytes" }
Write-Host "백업 완료: $($backupFile.FullName) ($([math]::Round($backupFile.Length/1kb,1))KB)"
Write-Host '복원 검증은 운영과 분리된 빈 DB에서 pg_restore --no-owner --no-acl 로 수행한다. (OPERATIONS 7절)'
