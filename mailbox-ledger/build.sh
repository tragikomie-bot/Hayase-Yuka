#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Linux x86_64: Java 17+, curl, unzip, Python 3. No Gradle required.
QINGLAN_TOOLS="${QINGLAN_TOOLS:-$PWD/.toolchain}"
QINGLAN_SDK="${QINGLAN_SDK:-$QINGLAN_TOOLS/sdk}"
QINGLAN_ECJ="${QINGLAN_ECJ:-$QINGLAN_TOOLS/ecj.jar}"
mkdir -p "$QINGLAN_TOOLS" "$QINGLAN_SDK" build/classes build/dex build/compiled signing output
if [ ! -f "$QINGLAN_SDK/android-35/android.jar" ]; then
  curl -fL --retry 2 -o "$QINGLAN_TOOLS/platform.zip" https://dl.google.com/android/repository/platform-35_r02.zip
  unzip -qo "$QINGLAN_TOOLS/platform.zip" -d "$QINGLAN_SDK"
fi
if [ ! -f "$QINGLAN_SDK/android-15/aapt2" ]; then
  curl -fL --retry 2 -o "$QINGLAN_TOOLS/build-tools.zip" https://dl.google.com/android/repository/build-tools_r35_linux.zip
  unzip -qo "$QINGLAN_TOOLS/build-tools.zip" -d "$QINGLAN_SDK"
fi
if [ ! -f "$QINGLAN_ECJ" ]; then
  curl -fL --retry 2 -o "$QINGLAN_ECJ" https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.38.0/ecj-3.38.0.jar
fi
if [ ! -f signing/password.txt ]; then
  python3 - <<'PY'
from pathlib import Path
import secrets
p=Path('signing/password.txt'); p.write_text(secrets.token_urlsafe(28)); p.chmod(0o600)
PY
fi
if [ ! -f signing/qinglan-release.p12 ]; then
  keytool -genkeypair -keystore signing/qinglan-release.p12 -storetype PKCS12 -storepass:file signing/password.txt -keypass:file signing/password.txt -alias qinglan -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Qinglan Ledger, OU=Personal App, O=Qinglan, C=CN'
fi
BT="$QINGLAN_SDK/android-15"
java -jar "$QINGLAN_ECJ" -8 -nowarn -classpath "$QINGLAN_SDK/android-35/android.jar" -d build/classes app/src/main/java/cn/qinglan/ledger/MainActivity.java
"$BT/d8" --lib "$QINGLAN_SDK/android-35/android.jar" --min-api 26 --output build/dex build/classes/cn/qinglan/ledger/*.class
"$BT/aapt2" compile --dir app/src/main/res -o build/compiled
"$BT/aapt2" link -o build/base.apk --manifest app/src/main/AndroidManifest.xml -I "$QINGLAN_SDK/android-35/android.jar" -A app/src/main/assets --min-sdk-version 26 --target-sdk-version 35 build/compiled/*.flat
python3 - <<'PY'
from zipfile import ZipFile,ZIP_DEFLATED
with ZipFile('build/base.apk','a') as z:z.write('build/dex/classes.dex','classes.dex',compress_type=ZIP_DEFLATED)
PY
"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk
"$BT/apksigner" sign --ks signing/qinglan-release.p12 --ks-key-alias qinglan --ks-pass file:signing/password.txt --out output/mailbox-ledger-1.0.0.apk build/aligned.apk
"$BT/apksigner" verify --verbose output/mailbox-ledger-1.0.0.apk
"$BT/zipalign" -c 4 output/mailbox-ledger-1.0.0.apk
