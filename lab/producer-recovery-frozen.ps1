$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $root 'lab/out/d3-stream-runtime-20261009'
$evidence=Join-Path $runtime 'producer-recovery-r87'
$java='D:/work/Java/jdk-17.0.7/bin'
$classpath="$root/target/classes;$evidence"
function Invoke-Probe($name,$arguments) {
    $arguments | ConvertTo-Json | Set-Content "$evidence/$name.command.json" -Encoding utf8
    & "$java/java.exe" @arguments 1> "$evidence/$name.stdout" 2> "$evidence/$name.stderr"
    $rc=$LASTEXITCODE
    Set-Content "$evidence/$name.rc" $rc
    if($rc -ne 0){throw "$name rc=$rc"}
}
& "$java/javac.exe" -cp $classpath -d $evidence "$root/lab/ProducerRecoveryProbe.java" 1> "$evidence/compile.stdout" 2> "$evidence/compile.stderr"
Set-Content "$evidence/compile.rc" $LASTEXITCODE
if($LASTEXITCODE -ne 0){throw 'probe compile failed'}
$summary=@()
foreach($case in @(
    @{name='business';export='physical-state-r70/export.command.json';closed='row-state-r65/resume.command.json';leaf='50359491';offset=9;expected=46},
    @{name='sys-corrected';export='pending-sys-state-r75/export.command.json';closed='pending-sys-state-r75/resume.command.json';leaf='50357659';offset=9;expected=2}
)) {
    $prior=Get-Content "$runtime/$($case.export)" -Raw | ConvertFrom-Json
    $full=Get-Content "$runtime/$($case.closed)" -Raw | ConvertFrom-Json
    $state="$evidence/$($case.name).state"
    $base=@('-Xmx768m','-cp',$classpath,'ProducerRecoveryProbe')
    if(!(Test-Path "$evidence/$($case.name)-export.rc")) {
        Invoke-Probe "$($case.name)-export" ($base+@('export',$state,'-',$prior[6],$prior[7],$case.leaf)+$prior[$case.offset..($prior.Length-1)])
    } elseif((Get-Content "$evidence/$($case.name)-export.rc") -ne '0'){throw 'prior export failed; preserve it and use a new case name'}
    $sha=(Get-FileHash -LiteralPath $state -Algorithm SHA256).Hash.ToLowerInvariant()
    $start=10
    if(!(Test-Path "$evidence/$($case.name)-resume.rc")) {
        Invoke-Probe "$($case.name)-resume" ($base+@('resume',$state,$sha,$prior[6],$prior[7],$case.leaf)+$full[$start..($full.Length-1)])
    } elseif((Get-Content "$evidence/$($case.name)-resume.rc") -ne '0'){throw 'prior resume failed; preserve it and use a new case name'}
    $output=Get-Content "$evidence/$($case.name)-resume.stdout"
    if($output -notcontains "EVENTS=$($case.expected)"){throw 'event count changed'}
    $summary+=@{case=$case.name;checkpointSha256=$sha;events=$case.expected;completeEventsAndBatchEqualStrict=$true;separateJvm=$true}
}
@{cases=$summary;sourceActions=0;productChanged=$false;rawPrefixStillRequired=$true;retentionAuthorized=$false} | ConvertTo-Json -Depth 5 | Set-Content "$evidence/summary.json" -Encoding utf8
