#!/system/bin/sh
set -e

# 本项目使用 javac + d8 + aapt2 + apksigner 直接构建，无需 Gradle。
# 需要环境变量 ANDROID_HOME 指向 Android SDK；aapt2/d8/apksigner/zip 在 PATH 中。
P=$(cd "$(dirname "$0")" && pwd)
cd "$P"

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
JAR="$SDK/platforms/android-35/android.jar"
if [ ! -f "$JAR" ]; then
  echo "找不到 android.jar: $JAR" >&2
  echo "请设置 ANDROID_HOME，或安装 platforms;android-35" >&2
  exit 1
fi

rm -rf build
mkdir -p build/classes build/dex

javac -source 8 -target 8 -bootclasspath "$JAR" -cp "$JAR" \
  -d build/classes \
  $(find src -type f -name '*.java' | sort)

d8 --release --min-api 21 --lib "$JAR" --output build/dex \
  $(find build/classes -type f -name '*.class' | sort)

aapt2 compile --dir res -o build/compiled-res.zip
aapt2 link -o build/unsigned.apk \
  --manifest AndroidManifest.xml \
  -I "$JAR" \
  --min-sdk-version 21 \
  --target-sdk-version 35 \
  build/compiled-res.zip

cp build/unsigned.apk build/withdex.apk
( cd build && zip -q -j withdex.apk dex/classes.dex )

if [ ! -f debug.keystore ]; then
  keytool -genkeypair \
    -keystore debug.keystore \
    -alias androiddebugkey \
    -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

apksigner sign \
  --ks debug.keystore \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out build/算算大时钟.apk \
  build/withdex.apk

apksigner verify --verbose build/算算大时钟.apk
ls -lh build/算算大时钟.apk
