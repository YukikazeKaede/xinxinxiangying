"""Build a separate instrumentation APK; never shipped with the application."""
from pathlib import Path
import os, subprocess, zipfile

root=Path(__file__).resolve().parents[2]
jdk=next((root/'toolchain/jdk').glob('*/bin'))
tools=root/'toolchain/sdk/android-16'
android=root/'toolchain/sdk/android-36/android.jar'
alipay=root/'toolchain/alipay/15.8.42/classes.jar'
if not alipay.is_file():
    raise SystemExit('Build the application with build.ps1 first to prepare the pinned Alipay SDK.')
app_classpath=os.pathsep.join((str(root/'build/classes'),str(alipay)))
out=root/'build/instrumentation'
for name in ('classes','dex','linked'):(out/name).mkdir(parents=True,exist_ok=True)
def run(*args):subprocess.run([str(a) for a in args],check=True,cwd=root)
run(tools/'aapt2.exe','link','--output-to-dir','-o',out/'linked','-I',android,'--manifest',root/'tests/android/AndroidManifest.xml')
run(jdk/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(android)+';'+str(tools/'core-lambda-stubs.jar'),'-cp',app_classpath,'-d',out/'classes',*sorted((root/'tests/android').glob('*.java')))
run(jdk/'java.exe','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--min-api','24','--lib',android,'--classpath',root/'build/classes','--classpath',alipay,'--output',out/'dex',*list((out/'classes').rglob('*.class')))
with zipfile.ZipFile(out/'unsigned.apk','w',zipfile.ZIP_STORED) as z:
    for file in (out/'linked').rglob('*'):
        if file.is_file():z.write(file,file.relative_to(out/'linked').as_posix())
    z.write(out/'dex/classes.dex','classes.dex')
keystore=Path(os.environ.get('XINXIN_TEST_KEYSTORE',root/'_private/signing/development.p12'))
password=os.environ.get('XINXIN_KEYSTORE_PASSWORD')
if password and keystore.exists():
    run(jdk/'java.exe','-jar',tools/'lib/apksigner.jar','sign','--ks',keystore,
        '--ks-key-alias',os.environ.get('XINXIN_KEY_ALIAS','xinxin'),
        '--ks-pass','env:XINXIN_KEYSTORE_PASSWORD','--out',out/'test.apk',out/'unsigned.apk')
    print('Built signed instrumentation test APK:', out/'test.apk')
else:
    print('Built unsigned instrumentation APK:', out/'unsigned.apk')
    print('Set XINXIN_TEST_KEYSTORE and XINXIN_KEYSTORE_PASSWORD to sign (keystore is not committed).')
