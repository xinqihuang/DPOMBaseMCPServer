# Cross-repo contract fixture generator: serialize a fixed evidence package using
# DPOMAgent's real PackageSerializer.
# Usage: pwsh -File scripts/regenerate-obs-contract-fixture.ps1
# Output: agentic-monitoring/src/test/resources/obs-fixtures/dpomagent-package.zip
# Also prints SHA-256 for the drift constant in PackageSerializerContractTest.
#
# Tool resolution: prefer JAVA_HOME / MAVEN_HOME / M2_HOME, then PATH; fail with a clear
# message when missing. Temp files (classpath, javac output) go to the system temp dir and
# are cleaned up in finally, so the DPOMAgent working tree is never modified.

$ErrorActionPreference = 'Stop'

function Resolve-Tool {
    param(
        [Parameter(Mandatory = $true)][string] $Name,
        [string] $EnvHome,
        [string] $Exe
    )
    if ($EnvHome) {
        $candidate = Join-Path (Join-Path $EnvHome 'bin') $Exe
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    throw "Could not find $Name. Set JAVA_HOME (or MAVEN_HOME/M2_HOME) or add $Name to PATH."
}

$mavenHome = if ($env:MAVEN_HOME) { $env:MAVEN_HOME } elseif ($env:M2_HOME) { $env:M2_HOME } else { $null }
$javaCmd = Resolve-Tool -Name 'java' -EnvHome $env:JAVA_HOME -Exe 'java.exe'
$javacCmd = Resolve-Tool -Name 'javac' -EnvHome $env:JAVA_HOME -Exe 'javac.exe'
$mvnCmd = Resolve-Tool -Name 'mvn' -EnvHome $mavenHome -Exe 'mvn.cmd'

$dpmRoot = 'D:\code\DPOMAgent'
$mcpRoot = 'D:\code\DPOMBaseMCPServer'
$fixture = Join-Path $mcpRoot 'agentic-monitoring\src\test\resources\obs-fixtures\dpomagent-package.zip'
$genSrc = Join-Path $mcpRoot 'scripts\ObsContractFixtureGenerator.java'

$workDir = Join-Path $env:TEMP ('obs-contract-gen-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $workDir | Out-Null

try {
    $cpFile = Join-Path $workDir 'cp.txt'

    Write-Host "[1/4] compile DPOMAgent agent-core (real PackageSerializer)"
    Push-Location $dpmRoot
    try {
        & $mvnCmd -q -pl agent-core -am compile
        if ($LASTEXITCODE -ne 0) {
            throw "DPOMAgent agent-core compile failed (exit $LASTEXITCODE)"
        }
        & $mvnCmd -q -pl agent-core dependency:build-classpath "-Dmdep.outputFile=$cpFile" '-Dmdep.includeScope=test'
        if ($LASTEXITCODE -ne 0) {
            throw "DPOMAgent agent-core build-classpath failed (exit $LASTEXITCODE)"
        }
    }
    finally {
        Pop-Location
    }
    $classpath = Get-Content $cpFile -Raw

    Write-Host "[2/4] compile fixture generator"
    & $javacCmd -encoding UTF-8 -cp "$dpmRoot\agent-core\target\classes;$classpath" -d $workDir $genSrc
    if ($LASTEXITCODE -ne 0) {
        throw "fixture generator compile failed (exit $LASTEXITCODE)"
    }

    Write-Host "[3/4] run generator -> $fixture"
    & $javaCmd -cp "$workDir;$dpmRoot\agent-core\target\classes;$classpath" ObsContractFixtureGenerator $fixture
    if ($LASTEXITCODE -ne 0) {
        throw "fixture generator run failed (exit $LASTEXITCODE)"
    }

    Write-Host "[4/4] sha256"
    $hash = (Get-FileHash -Algorithm SHA256 $fixture).Hash.ToLower()
    Write-Host "fixture=$fixture"
    Write-Host "sha256=$hash"
}
finally {
    Remove-Item -Recurse -Force $workDir -ErrorAction SilentlyContinue
}
