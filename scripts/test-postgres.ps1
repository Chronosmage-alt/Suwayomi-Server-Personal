#Requires -Version 7.0
<# Opt-in only. Requires local PostgreSQL binaries and Java 21; never uses Docker,
   an existing PostgreSQL service, a supplied DSN, or production application data. #>
[CmdletBinding()]
param([Parameter(Mandatory)][string]$PostgresBin)
$ErrorActionPreference = 'Stop'
if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'release')) -or
    !(Select-String -LiteralPath (Join-Path $env:JAVA_HOME 'release') -Pattern '^JAVA_VERSION="21[.\"]' -Quiet)) {
    throw 'Set JAVA_HOME to a Java 21 JDK (required by the test-only socket guard).'
}
$repo = Split-Path $PSScriptRoot -Parent
$pgBin = (Resolve-Path -LiteralPath $PostgresBin).Path
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
foreach ($localPath in $repo, $pgBin, $tempRoot) {
    if ($localPath.StartsWith('\\') -or [IO.DriveInfo]::new([IO.Path]::GetPathRoot($localPath)).DriveType -ne 'Fixed') {
        throw 'Repository, PostgreSQL binaries and temporary directory must be on local fixed drives.'
    }
}
foreach ($tool in 'initdb', 'pg_ctl', 'psql') {
    if (!(Test-Path -LiteralPath (Join-Path $pgBin "$tool.exe"))) { throw "Missing local $tool.exe" }
}
$runId = [Guid]::NewGuid().ToString('N')
$root = Join-Path $tempRoot "suwayomi-pg-$runId"
if (Test-Path -LiteralPath $root) { throw 'Refusing to reuse any directory' }
New-Item -ItemType Directory -Path $root | Out-Null
# Restrict generated credentials and disposable data to the current Windows user.
$acl = Get-Acl -LiteralPath $root
$acl.SetAccessRuleProtection($true, $false)
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
Set-Acl -LiteralPath $root -AclObject $acl
$token = [Guid]::NewGuid().ToString('N')
[IO.File]::WriteAllText((Join-Path $root 'owner.token'), $token)
$cluster = Join-Path $root 'cluster'
$password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$appPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$role = "suwayomi_pg_$runId"
$fresh = "${role}_fresh"
$upgrade = "${role}_upgrade"
$pwFile = Join-Path $root 'init-password'
$manifest = Join-Path $root 'connection.properties'
$provision = Join-Path $root 'provision.sql'
$savedEnvironment = @{}
Get-ChildItem Env: | Where-Object { $_.Name -match '^(PG|SUWAYOMI_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)' } | ForEach-Object {
    $savedEnvironment[$_.Name] = $_.Value
    [Environment]::SetEnvironmentVariable($_.Name, $null, 'Process')
}
$started = $false
function Invoke-LocalTool([string]$name, [string[]]$arguments) {
    if ($name -eq 'pg_ctl') {
        # postgres must not inherit PowerShell's output pipe (it would keep it open).
        $quoted = ($arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' '
        $process = Start-Process -FilePath (Join-Path $pgBin 'pg_ctl.exe') -ArgumentList $quoted -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'pg-ctl.stdout') -RedirectStandardError (Join-Path $root 'pg-ctl.stderr')
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "pg_ctl failed (see $root/pg-ctl.stderr)" }
        return
    }
    & (Join-Path $pgBin "$name.exe") @arguments
    if ($LASTEXITCODE -ne 0) { throw "$name failed with exit code $LASTEXITCODE (see $root)" }
}
function Assert-OwnedCluster {
    if ((Get-Content -LiteralPath (Join-Path $root 'owner.token') -Raw) -ne $token) { throw 'Ownership marker mismatch' }
    if ([IO.Path]::GetFullPath($cluster) -ne (Join-Path $root 'cluster')) { throw 'Unexpected cluster path' }
}
function Start-Cluster {
    Assert-OwnedCluster
    Invoke-LocalTool 'pg_ctl' @('-D', $cluster, '-l', (Join-Path $root 'postgres.log'), '-w', 'start')
}
function Invoke-Validation([string]$phase) {
    $env:SUWAYOMI_PG_MANIFEST = $manifest
    $env:SUWAYOMI_PG_PHASE = $phase
    $env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
    $log = Join-Path $root "$phase-gradle.log"
    $phaseStart = Get-Date
    Push-Location $repo
    try {
        & ./gradlew.bat -I docker/adaptive/build.init.gradle :server:postgresValidation --no-daemon --max-workers=1 --console=plain '-Dorg.gradle.jvmargs=-Xmx3072m' '-Pkotlin.compiler.execution.strategy=in-process' *> $log
        $exitCode = $LASTEXITCODE
        $reports = Join-Path $repo 'server/build/test-results/postgresValidation'
        if (Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | Where-Object LastWriteTime -ge $phaseStart) {
            Copy-Item -LiteralPath $reports -Destination (Join-Path $root "$phase-results") -Recurse
        }
        if ($exitCode -ne 0) { throw "PostgreSQL $phase tests failed: $log" }
    } finally { Pop-Location }
}
function Snapshot([string]$database, [string]$name) {
    # Generate sorted JSON rows for every application table, including migration metadata.
    $sql = Join-Path $root 'snapshot.sql'
    @'
SELECT format('SELECT %L || COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text, ''[]'') FROM suwayomi.%I t;', table_name || ':', table_name)
FROM information_schema.tables WHERE table_schema='suwayomi' ORDER BY table_name
\gexec
'@ | Set-Content -LiteralPath $sql -Encoding utf8NoBOM
    $output = & (Join-Path $pgBin 'psql.exe') -X -w -h 127.0.0.1 -p $port -U postgres -d $database -v ON_ERROR_STOP=1 -At -f $sql
    if ($LASTEXITCODE -ne 0) { throw 'Snapshot failed' }
    $output | Set-Content -LiteralPath (Join-Path $root "$name.snapshot") -Encoding utf8NoBOM
}
try {
    [IO.File]::WriteAllText($pwFile, $password)
    Invoke-LocalTool 'initdb' @('-D', $cluster, '-U', 'postgres', '--pwfile', $pwFile, '--auth=scram-sha-256', '--encoding=UTF8', '--locale=C')
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    @"
listen_addresses = '127.0.0.1'
port = $port
max_connections = 30
shared_buffers = '32MB'
log_statement = 'none'
log_min_error_statement = 'panic'
"@ | Add-Content -LiteralPath (Join-Path $cluster 'postgresql.conf')
    Start-Cluster
    $started = $true
    $env:PGPASSWORD = $password
    @"
REVOKE CONNECT ON DATABASE postgres, template1 FROM PUBLIC;
CREATE ROLE $role LOGIN PASSWORD '$appPassword' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE DATABASE $fresh OWNER $role;
CREATE DATABASE $upgrade OWNER $role;
REVOKE ALL ON DATABASE $fresh, $upgrade FROM PUBLIC;
\connect $fresh
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE TABLE public.harness_owner (token text NOT NULL);
INSERT INTO public.harness_owner VALUES ('$token');
GRANT SELECT ON public.harness_owner TO $role;
\connect $upgrade
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE TABLE public.harness_owner (token text NOT NULL);
INSERT INTO public.harness_owner VALUES ('$token');
GRANT SELECT ON public.harness_owner TO $role;
"@ | Set-Content -LiteralPath $provision -Encoding utf8NoBOM
    Invoke-LocalTool 'psql' @('-X', '-w', '-h', '127.0.0.1', '-p', "$port", '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-f', $provision)
    @"
root=$($root.Replace('\','/'))
token=$token
port=$port
user=$role
password=$appPassword
fresh=$fresh
upgrade=$upgrade
"@ | Set-Content -LiteralPath $manifest -Encoding ascii
    # Child JVM gets only the limited role's manifest; never the cluster administrator password.
    Remove-Item Env:PGPASSWORD
    Write-Host "Disposable PostgreSQL validation: $root"
    Invoke-Validation 'prepare'
    $env:PGPASSWORD = $password
    foreach ($database in $fresh, $upgrade) { Snapshot $database "$database-before" }
    Assert-OwnedCluster
    Invoke-LocalTool 'pg_ctl' @('-D', $cluster, '-m', 'fast', '-w', 'stop')
    $started = $false
    Start-Cluster
    $started = $true
    foreach ($database in $fresh, $upgrade) {
        Snapshot $database "$database-after"
        if ((Get-FileHash (Join-Path $root "$database-before.snapshot")).Hash -ne (Get-FileHash (Join-Path $root "$database-after.snapshot")).Hash) { throw 'Database contents changed across PostgreSQL restart' }
    }
    Remove-Item Env:PGPASSWORD
    Invoke-Validation 'restart'
    $leaked = Get-ChildItem -LiteralPath $root -Recurse -File -Include '*.log', '*.xml', 'server.conf' |
        Select-String -SimpleMatch -Pattern $password, $appPassword -List
    if ($leaked) { throw 'Generated credentials appeared in a test report or log; review the private run directory.' }
    Write-Host "PASS: fresh install, M0069 upgrade, constraints, Komga mock tests and restart snapshots. Reports: $root"
} finally {
    try {
        if ($started -or (Test-Path -LiteralPath (Join-Path $cluster 'postmaster.pid'))) {
            Assert-OwnedCluster
            Invoke-LocalTool 'pg_ctl' @('-D', $cluster, '-m', 'fast', '-w', 'stop')
        }
    } finally {
        foreach ($file in $pwFile, $manifest, $provision) {
            if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
        }
        Get-ChildItem Env: | Where-Object { $_.Name -match '^(PG|SUWAYOMI_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)' } | ForEach-Object { [Environment]::SetEnvironmentVariable($_.Name, $null, 'Process') }
        foreach ($key in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key], 'Process') }
    }
}
