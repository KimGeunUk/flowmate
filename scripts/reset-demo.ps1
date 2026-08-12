# FlowMate 데모 데이터 초기화.
#
# 공개 데모에서는 방문자가 문서를 승인·반려하므로 다음 방문자가 어질러진 화면을
# 본다. 시드가 스크립트라서 DB 만 다시 만들면 원상복구된다.
#
# ★ docker compose down -v 를 쓰지 않는다. 볼륨을 지우면 컨테이너 재생성까지
#   따라오고 다른 상태(네트워크·이미지 캐시)도 함께 흔든다. DROP DATABASE 는
#   딱 필요한 것만 지운다.
#
# ★ 이 파일은 UTF-8 BOM 으로 저장해야 한다. Windows PowerShell 5.1 은 BOM 이 없는
#   .ps1 을 시스템 ANSI 코드페이지(한국어 Windows 에서는 CP949)로 읽어서, 주석의
#   한글이 깨지는 데서 끝나지 않고 파서가 문자열의 끝을 잘못 잡아 스크립트 전체가
#   구문 오류로 죽는다. BOM 을 지우는 편집기로 저장하지 말 것.
#
# 사용: powershell -ExecutionPolicy Bypass -File scripts\reset-demo.ps1

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "[1/6] Tomcat 중지 (DB 연결을 끊는다)"
docker compose stop tomcat | Out-Null

Write-Host "[2/6] 데이터베이스 재생성"
# WITH (FORCE) 는 PostgreSQL 13+ 기능. 남은 연결을 끊고 지운다.
docker compose exec -T postgres psql -U flowmate -d postgres -v ON_ERROR_STOP=1 `
    -c "DROP DATABASE IF EXISTS flowmate WITH (FORCE);" | Out-Null
docker compose exec -T postgres psql -U flowmate -d postgres -v ON_ERROR_STOP=1 `
    -c "CREATE DATABASE flowmate;" | Out-Null

Write-Host "[3/6] 시드 스크립트 재실행"
# 초기화 스크립트는 컨테이너에 읽기 전용으로 마운트돼 있으므로 그대로 쓴다.
# 파일명 순서가 곧 의존 순서다(스키마 -> 시드).
$scripts = @(
    '00-extension.sql', '10-schema-org.sql', '11-seed-org.sql',
    '20-schema-approval.sql', '21-seed-approval.sql', '30-schema-ai.sql',
    '40-schema-attendance.sql', '41-seed-attendance.sql',
    '50-seed-demo.sql', '60-schema-ai-features.sql'
)
foreach ($s in $scripts) {
    Write-Host "      - $s"
    docker compose exec -T postgres psql -U flowmate -d flowmate -q -v ON_ERROR_STOP=1 `
        -f "/docker-entrypoint-initdb.d/$s" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "시드 실패: $s" }
}

Write-Host "[4/6] 업로드된 첨부 파일 삭제"
# 첨부 행은 DB 와 함께 지워졌으므로 디스크에 남은 파일은 전부 고아다.
$uploadDir = Join-Path $projectRoot 'upload'
if (Test-Path $uploadDir) {
    Get-ChildItem -Path $uploadDir -Force | Remove-Item -Recurse -Force -Confirm:$false
}

Write-Host "[5/6] Tomcat 재기동"
docker compose start tomcat | Out-Null

Write-Host "[6/6] 복구 확인"
# 시드가 실제로 들어갔는지, 앱이 응답하는지까지 봐야 "초기화됐다"고 할 수 있다.
$docs = (docker compose exec -T postgres psql -U flowmate -d flowmate -tAc `
    "SELECT count(*) FROM approval_doc;").Trim()
Write-Host "      결재 문서 $docs 건"
if ([int]$docs -lt 200) { throw "시드가 덜 들어갔다: approval_doc $docs 건" }

# ★ Invoke-WebRequest 를 쓰지 않는다. Windows PowerShell 5.1 에는
#   -SkipHttpErrorCheck 가 없어서 302 를 예외로 던지고, 그러면 정상 응답과
#   기동 실패가 구별되지 않는다(둘 다 catch 로 떨어진다). curl.exe 는 상태
#   코드를 그대로 돌려준다. PowerShell 에서 curl 은 Invoke-WebRequest 의
#   별칭이므로 반드시 확장자까지 붙여 부른다.
$deadline = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 3
    $code = (curl.exe -s -o NUL -w "%{http_code}" --max-time 5 `
        'http://localhost:18080/flowmate')
} while ($code -ne '302' -and (Get-Date) -lt $deadline)

if ($code -ne 302) { throw "앱이 응답하지 않는다 (마지막 응답 코드: $code)" }
Write-Host "      앱 응답 정상 (302)"

Write-Host "완료: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
