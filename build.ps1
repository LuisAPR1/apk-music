param(
    [string]$SdkRoot = (Join-Path $PSScriptRoot '..\.toolchain\sdk'),
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
function Run([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed ($LASTEXITCODE)" }
}
if (-not $JavaHome) { throw 'Set JAVA_HOME to a JDK (17 or newer).' }
$javaBin = Join-Path $JavaHome 'bin'
$bt = Join-Path $SdkRoot 'build-tools\36.0.0'
$androidJar = Join-Path $SdkRoot 'platforms\android-36\android.jar'
if (-not (Test-Path $androidJar)) { throw 'Install Android SDK platform 36 and build-tools 36.0.0. Pass -SdkRoot with the SDK directory.' }
$out = Join-Path $PSScriptRoot 'build'
# A fresh numbered build directory avoids stale bytecode without deleting prior builds.
$work = Join-Path $out ('work-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
$classes = Join-Path $work 'classes'
$generated = Join-Path $work 'generated'
$dex = Join-Path $work 'dex'
New-Item -ItemType Directory -Force $classes,$generated,$dex | Out-Null
$res = Join-Path $PSScriptRoot 'app\src\main\res'
$manifest = Join-Path $PSScriptRoot 'app\src\main\AndroidManifest.xml'
# AGP obtains the package from namespace; standalone aapt2 needs it in its input.
[xml]$manifestXml = Get-Content -Raw $manifest
$manifestXml.DocumentElement.SetAttribute('package', 'pt.luis.oledmusic')
$generatedManifest = Join-Path $work 'AndroidManifest.xml'
$manifestXml.Save($generatedManifest)
Run (Join-Path $bt 'aapt2.exe') @('compile','--dir',$res,'-o',"$work\resources.zip")
Run (Join-Path $bt 'aapt2.exe') @('link','-I',$androidJar,'--manifest',$generatedManifest,'--min-sdk-version','26','--target-sdk-version','36','--version-code','2','--version-name','1.1','--java',$generated,'-o',"$work\unsigned.apk","$work\resources.zip")
$sources = @(Get-ChildItem (Join-Path $PSScriptRoot 'app\src\main\java'),$generated -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
$javacArgs = @('--release','8','-encoding','UTF-8','-classpath',$androidJar,'-d',$classes) + $sources
Run (Join-Path $javaBin 'javac.exe') $javacArgs
Run (Join-Path $javaBin 'jar.exe') @("-J-Djava.io.tmpdir=$work",'cf',"$work\classes.jar",'-C',$classes,'.')
Run (Join-Path $javaBin 'java.exe') @('-cp',"$bt\lib\d8.jar",'com.android.tools.r8.D8','--min-api','26','--lib',$androidJar,'--output',$dex,"$work\classes.jar")
Run (Join-Path $javaBin 'jar.exe') @("-J-Djava.io.tmpdir=$work",'uf',"$work\unsigned.apk",'-C',$dex,'classes.dex')
Run (Join-Path $bt 'zipalign.exe') @('-f','4',"$work\unsigned.apk","$work\aligned.apk")
$key = Join-Path $PSScriptRoot 'oledmusic-local.jks'
if (-not (Test-Path $key)) {
    Run (Join-Path $javaBin 'keytool.exe') @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','oledmusic','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=OLED Music Personal Build','-storetype','JKS')
}
$apk = Join-Path $out 'OLED-Music.apk'
Run (Join-Path $javaBin 'java.exe') @('-jar',"$bt\lib\apksigner.jar",'sign','--ks',$key,'--ks-key-alias','oledmusic','--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,"$work\aligned.apk")
Run (Join-Path $javaBin 'java.exe') @('-jar',"$bt\lib\apksigner.jar",'verify','--verbose',$apk)
Run (Join-Path $bt 'zipalign.exe') @('-c','4',$apk)
Get-FileHash -Algorithm SHA256 $apk | Format-List
Write-Host "APK: $apk"
