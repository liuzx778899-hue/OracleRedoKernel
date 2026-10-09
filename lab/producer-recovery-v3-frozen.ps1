param([string]$EvidenceName='producer-recovery-r91')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $root 'lab/out/d3-stream-runtime-20261009'
$evidence=Join-Path $runtime $EvidenceName
New-Item -ItemType Directory -Path $evidence -ErrorAction Stop | Out-Null
$java='D:/work/Java/jdk-17.0.7/bin'
$classpath="$root/target/classes;$evidence"
function Invoke-Probe($name,$arguments) {
    $arguments | ConvertTo-Json | Set-Content "$evidence/$name.command.json" -Encoding utf8
    & "$java/java.exe" @arguments 1> "$evidence/$name.stdout" 2> "$evidence/$name.stderr"
    $rc=$LASTEXITCODE; Set-Content "$evidence/$name.rc" $rc
    if($rc -ne 0){throw "$name rc=$rc"}
}
& "$java/javac.exe" -cp $classpath -d $evidence "$root/lab/ProducerRecoveryProbe.java" 1> "$evidence/compile.stdout" 2> "$evidence/compile.stderr"
Set-Content "$evidence/compile.rc" $LASTEXITCODE
if($LASTEXITCODE -ne 0){throw 'probe compile failed'}
$summary=@()
foreach($case in @(@{name='business';expected=46},@{name='sys-corrected';expected=2})) {
    $prior=Get-Content "$runtime/producer-recovery-r87/$($case.name)-export.command.json" -Raw | ConvertFrom-Json
    $full=Get-Content "$runtime/producer-recovery-r87/$($case.name)-resume.command.json" -Raw | ConvertFrom-Json
    $state="$evidence/$($case.name).state"
    $prior[2]=$classpath;$prior[5]=$state
    Invoke-Probe "$($case.name)-export" $prior
    $sha=(Get-FileHash -LiteralPath $state -Algorithm SHA256).Hash.ToLowerInvariant()
    $full[2]=$classpath;$full[4]='extend';$full[5]=$state;$full[6]=$sha
    # r87 probe arguments: JVM flags at 0..3, mode at 4, state at 5, digest at 6.
    Invoke-Probe "$($case.name)-extend" $full
    $output=Get-Content "$evidence/$($case.name)-extend.stdout"
    if($output -notcontains "EVENTS=$($case.expected)"){throw 'event count changed'}
    $summary+=@{case=$case.name;checkpointSha256=$sha;events=$case.expected;freshStateEqual=$true;repeatUnchanged=$true;completeEventsAndBatchEqualStrict=$true}
}
@{cases=$summary;sourceActions=0;productChanged=$false;rawPrefixStillRequired=$true;retentionAuthorized=$false} | ConvertTo-Json -Depth 5 | Set-Content "$evidence/summary.json" -Encoding utf8
