$ErrorActionPreference = "Stop"

$baseUrl = "http://127.0.0.1:18080"
$tcpHost = "127.0.0.1"
$tcpPort = 19090
$caller = "u_state_caller"
$callee = "u_state_callee"
$conversationId = "c_state_" + [Guid]::NewGuid().ToString("N")

function New-TcpClientSession {
    param([string]$UserId)
    $client = [Net.Sockets.TcpClient]::new($tcpHost, $tcpPort)
    $stream = $client.GetStream()
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    $writer.AutoFlush = $true
    $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false))
    $auth = @{
        version = "1"
        type = "AUTH"
        requestId = "auth-$UserId"
        from = $UserId
        timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
        payload = @{ token = "demo-token-$UserId" }
    } | ConvertTo-Json -Compress
    $writer.WriteLine($auth)
    $line = $reader.ReadLine()
    if ($line -notmatch '"type":"AUTH_OK"') {
        throw "TCP auth failed for $UserId`: $line"
    }
    return [pscustomobject]@{ Client = $client; Reader = $reader; Writer = $writer; UserId = $UserId }
}

function Read-LineWithTimeout {
    param(
        [object]$Session,
        [int]$TimeoutMs = 8000
    )
    $task = $Session.Reader.ReadLineAsync()
    if (-not $task.Wait($TimeoutMs)) {
        throw "timeout waiting TCP event for $($Session.UserId)"
    }
    return $task.Result
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [string]$UserId,
        [object]$Body = $null
    )
    $headers = @{ Authorization = "Bearer demo-token-$UserId" }
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri "$baseUrl$Path" -Headers $headers
    }
    return Invoke-RestMethod -Method $Method -Uri "$baseUrl$Path" -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Compress)
}

Write-Host "Call state check" -ForegroundColor Cyan
$callerSession = New-TcpClientSession $caller
$calleeSession = New-TcpClientSession $callee
try {
    $start = Invoke-Json "Post" "/api/calls" $caller @{
        callerId = $caller
        calleeId = $callee
        conversationId = $conversationId
        mediaType = "audio"
    }
    if (-not $start.success -or $start.data.status -ne "ringing") {
        throw "call start failed: $($start | ConvertTo-Json -Depth 5 -Compress)"
    }
    $callId = $start.data.id

    $calleeInvite = Read-LineWithTimeout $calleeSession
    if ($calleeInvite -notmatch '"type":"CALL_INVITE"' -or $calleeInvite -notmatch '"calleeId":"' + $callee + '"') {
        throw "callee did not receive CALL_INVITE: $calleeInvite"
    }

    $callerUpdate = Read-LineWithTimeout $callerSession
    if ($callerUpdate -notmatch '"type":"CALL_UPDATE"' -or $callerUpdate -match '"type":"CALL_INVITE"') {
        throw "caller received wrong event: $callerUpdate"
    }

    $answer = Invoke-Json "Post" "/api/calls/$callId/answer" $callee @{ actorId = $callee }
    if (-not $answer.success -or $answer.data.status -ne "answered") {
        throw "answer failed: $($answer | ConvertTo-Json -Depth 5 -Compress)"
    }

    $callerAnswered = Read-LineWithTimeout $callerSession
    $calleeAnswered = Read-LineWithTimeout $calleeSession
    if ($callerAnswered -notmatch '"status":"answered"' -or $calleeAnswered -notmatch '"status":"answered"') {
        throw "participants did not receive answered update: caller=$callerAnswered callee=$calleeAnswered"
    }

    $badAnswerBlocked = $false
    try {
        Invoke-Json "Post" "/api/calls/$callId/answer" $caller @{ actorId = $caller } | Out-Null
    } catch {
        $badAnswerBlocked = $true
    }
    if (-not $badAnswerBlocked) {
        throw "caller was allowed to answer its own call"
    }

    Invoke-Json "Post" "/api/calls/$callId/hangup" $caller @{ actorId = $caller } | Out-Null
    Write-Host "PASS call state" -ForegroundColor Green
} finally {
    $callerSession.Client.Close()
    $calleeSession.Client.Close()
}
