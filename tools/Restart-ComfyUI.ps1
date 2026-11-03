[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ComfyRoot,
    [int]$Port = 8189
)

# This helper is intentionally launched by the user, not by an agent tool.
# The visible console keeps the server accessible for later manual restarts.
$ErrorActionPreference = 'Stop'
$Host.UI.RawUI.WindowTitle = "ComfyUI - servidor $Port"
$rootPath = (Resolve-Path -LiteralPath $ComfyRoot).Path
$pythonPath = Join-Path $rootPath '.venv\Scripts\python.exe'
$mainPath = Join-Path $rootPath 'main.py'
if (-not (Test-Path -LiteralPath $pythonPath) -or -not (Test-Path -LiteralPath $mainPath)) {
    throw 'Instalacao do ComfyUI nao encontrada. Nenhum processo foi encerrado.'
}

$listener = Get-NetTCPConnection -LocalAddress '127.0.0.1' -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    $server = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($server.ParentProcessId)"
    $belongsToComfy = $server.ExecutablePath -eq $pythonPath -or $parent.ExecutablePath -eq $pythonPath
    if (-not $belongsToComfy -or $server.CommandLine -notmatch '(^|[\s"])(main\.py|[^\s"]+[\\/]main\.py)([\s"]|$)') {
        throw "Porta $Port pertence a outro processo. Nenhum processo foi encerrado."
    }
    $address = "http://127.0.0.1:$Port"
    $queue = Invoke-RestMethod -Uri "$address/queue" -TimeoutSec 10
    if ($queue.queue_running.Count -gt 0 -or $queue.queue_pending.Count -gt 0) {
        throw 'Ha geracoes em andamento ou na fila. Aguarde terminarem e abra este atalho novamente.'
    }
    $backupDirectory = Join-Path $rootPath 'user\Momentos\restart-backups'
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    $backup = Join-Path $backupDirectory ("history-{0}.json" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Invoke-WebRequest -Uri "$address/history" -OutFile $backup -TimeoutSec 30 -UseBasicParsing
    Write-Host "Historico anterior guardado: $backup"
    Write-Host "Encerrando somente servidor ComfyUI da porta $Port (PID $($server.ProcessId))..."
    Stop-Process -Id $server.ProcessId -ErrorAction Stop
    Wait-Process -Id $server.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
}

Set-Location -LiteralPath $rootPath
Write-Host "Iniciando ComfyUI em http://127.0.0.1:$Port"
Write-Host 'Mantenha esta janela aberta. Ctrl+C encerra o servidor.'
& $pythonPath $mainPath --port $Port --enable-manager --preview-method auto --use-sage-attention
