param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipDocker,
    [switch]$SkipNativeBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$failed = @()
$blocked = @()

function Run-Step {
    param(
        [string]$Name,
        [scriptblock]$Command,
        [switch]$Optional
    )

    Write-Host ""
    Write-Host "==> $Name"
    try {
        & $Command
        Write-Host "PASS $Name" -ForegroundColor Green
    } catch {
        if ($Optional) {
            $script:blocked += "$Name`: $($_.Exception.Message)"
            Write-Host "BLOCKED $Name - $($_.Exception.Message)" -ForegroundColor Yellow
        } else {
            $script:failed += "$Name`: $($_.Exception.Message)"
            Write-Host "FAIL $Name - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath exited with code $LASTEXITCODE"
    }
}

if (-not $SkipBackend) {
    Run-Step "Backend tests" {
        Push-Location "$root\im-server"
        try {
            Invoke-Native "mvn" @("test")
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipFrontend) {
    Run-Step "Web build" {
        Push-Location "$root\im-ui"
        try {
            Invoke-Native "npm" @("run", "build")
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipDocker) {
    Run-Step "Docker compose config" {
        Push-Location $root
        try {
            Invoke-Native "docker" @("compose", "config")
        } finally {
            Pop-Location
        }
    } -Optional

    Run-Step "Docker server image available" {
        Push-Location $root
        try {
            & docker image inspect enterprise-im-server:0.1.0 *> $null
            if ($LASTEXITCODE -ne 0) {
                Invoke-Native "docker" @("build", "-t", "enterprise-im-server:0.1.0", "im-server")
            } else {
                Write-Host "enterprise-im-server:0.1.0 exists locally"
            }
        } finally {
            Pop-Location
        }
    } -Optional

    Run-Step "Docker compose runtime" {
        Push-Location $root
        try {
            Invoke-Native "docker" @("compose", "up", "-d")
            $health = $null
            for ($i = 0; $i -lt 30; $i++) {
                try {
                    $health = Invoke-RestMethod "http://127.0.0.1:18080/actuator/health"
                    if ($health.status -eq "UP") {
                        break
                    }
                } catch {
                    Start-Sleep -Seconds 2
                }
            }
            if ($null -eq $health) {
                throw "health endpoint did not respond"
            }
            if ($health.status -ne "UP") {
                throw "health status is $($health.status)"
            }
        } finally {
            Pop-Location
        }
    } -Optional

    Run-Step "Docker live call auth and connectivity" {
        $baseUrl = "http://127.0.0.1:18080"
        $adminLogin = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/admin/auth/login" -ContentType "application/json" -Body '{"phone":"18800000000","password":"admin123"}'
        if (-not $adminLogin.success -or -not $adminLogin.data.token) {
            throw "admin login failed"
        }

        $adminHeaders = @{ Authorization = "Bearer $($adminLogin.data.token)" }
        $connectivity = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/admin/call-connectivity" -Headers $adminHeaders
        if (-not $connectivity.success) {
            throw "call connectivity endpoint failed"
        }
        $checkNames = @($connectivity.data.checks | ForEach-Object { $_.name })
        if (-not ($checkNames -contains "turn") -or -not ($checkNames -contains "pjsipSignal")) {
            throw "call connectivity missing turn or pjsipSignal checks"
        }

        $userLogin = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" -ContentType "application/json" -Body '{"phone":"18812345678","password":"demo"}'
        if (-not $userLogin.success -or -not $userLogin.data.token) {
            throw "user login failed"
        }
        $userHeaders = @{ Authorization = "Bearer $($userLogin.data.token)" }
        $callConfig = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/calls/config" -Headers $userHeaders
        if (-not $callConfig.success -or -not $callConfig.data.turnPassword) {
            throw "authenticated call config failed"
        }

        $mediaConfig = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/calls/media-config?userId=$($userLogin.data.userId)&calleeId=u_qt" -Headers $userHeaders
        if (-not $mediaConfig.success -or -not $mediaConfig.data.sipRegistrar -or -not $mediaConfig.data.calleeSipUri) {
            throw "authenticated media config failed"
        }

        $blockedWithoutToken = $false
        try {
            Invoke-RestMethod -Method Get -Uri "$baseUrl/api/calls/config" | Out-Null
        } catch {
            $status = $_.Exception.Response.StatusCode.value__
            if ($status -eq 401) {
                $blockedWithoutToken = $true
            }
        }
        if (-not $blockedWithoutToken) {
            throw "call config without token was not rejected"
        }
    } -Optional

    Run-Step "Docker TURN allocation" {
        Invoke-Native "powershell" @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "$root\scripts\verify-turn-allocation.ps1")
    } -Optional
}

if (-not $SkipNativeBuild) {
    Run-Step "Qt toolchain build" {
        Invoke-Native "powershell" @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "$root\scripts\package-qt.ps1")
    } -Optional

    Run-Step "Flutter APK build" {
        Invoke-Native "powershell" @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "$root\scripts\package-flutter.ps1")
    } -Optional
}

Run-Step "Video capability report" {
    Invoke-Native "powershell" @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "$root\scripts\verify-video-capability.ps1")
} -Optional

Run-Step "Call state report" {
    Invoke-Native "powershell" @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "$root\scripts\verify-call-state.ps1")
} -Optional

Run-Step "Delivery artifacts" {
    $artifacts = @(
        "$root\im-server\target\im-server-0.1.0-SNAPSHOT.jar",
        "$root\dist\EnterpriseIMQtClient-vs2017.zip",
        "$root\dist\EnterpriseIMQtClient-vs2017\EnterpriseIMQtClient.exe",
        "$root\dist\enterprise-im-app-release.apk",
        "$root\project-docs\PJSIP_GATEWAY_CONTRACT.md"
    )
    foreach ($artifact in $artifacts) {
        if (-not (Test-Path $artifact)) {
            throw "missing $artifact"
        }
        $item = Get-Item $artifact
        if (-not $item.PSIsContainer -and $item.Length -le 0) {
            throw "empty $artifact"
        }
    }
}

Write-Host ""
Write-Host "== Summary =="
if ($failed.Count -eq 0) {
    Write-Host "Required checks passed." -ForegroundColor Green
} else {
    Write-Host "Required failures:" -ForegroundColor Red
    $failed | ForEach-Object { Write-Host "- $_" -ForegroundColor Red }
}

if ($blocked.Count -gt 0) {
    Write-Host "Blocked optional checks:" -ForegroundColor Yellow
    $blocked | ForEach-Object { Write-Host "- $_" -ForegroundColor Yellow }
}

if ($failed.Count -gt 0) {
    exit 1
}
