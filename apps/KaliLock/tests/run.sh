#!/usr/bin/env bash
set -Eeuo pipefail
BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BUILD="$(mktemp -d)"
trap 'rm -rf -- "$BUILD"' EXIT
javac -encoding UTF-8 -classpath "$BASE/build/install/classes:$SDK/platforms/android-35/android.jar" -d "$BUILD" "$BASE/tests/BootCryptoTest.java"
java -classpath "$BUILD:$BASE/build/install/classes:$SDK/platforms/android-35/android.jar" de.nethunter.flippernfc.BootCryptoTest
javac -encoding UTF-8 -d "$BUILD" "$BASE/tests/stubs/android/os/SystemClock.java" "$BASE/app/src/main/java/de/nethunter/flippernfc/UnlockTransaction.java" "$BASE/tests/UnlockTransactionTest.java"
java -classpath "$BUILD" de.nethunter.flippernfc.UnlockTransactionTest
mkdir -p "$BUILD/enrollment"
mapfile -t STUBS < <(find "$BASE/tests/enrollment-stubs" -name '*.java')
javac -encoding UTF-8 -classpath "$BASE/build/install/classes:$SDK/platforms/android-35/android.jar" -d "$BUILD/enrollment" "${STUBS[@]}" "$BASE/tests/stubs/android/os/SystemClock.java" "$BASE/app/src/main/java/de/nethunter/flippernfc/UnlockTransaction.java" "$BASE/app/src/main/java/de/nethunter/flippernfc/DirectBootUnlock.java" "$BASE/tests/EnrollmentRecoveryTest.java"
java -classpath "$BUILD/enrollment:$BASE/build/install/classes:$SDK/platforms/android-35/android.jar" de.nethunter.flippernfc.EnrollmentRecoveryTest
javac -encoding UTF-8 -d "$BUILD" "$BASE/app/src/main/java/de/nethunter/flippernfc/SetupSequence.java" "$BASE/tests/SetupSequenceTest.java"
java -classpath "$BUILD" de.nethunter.flippernfc.SetupSequenceTest
javac -encoding UTF-8 -d "$BUILD" "$BASE/app/src/main/java/de/nethunter/flippernfc/NfcTiming.java" "$BASE/tests/NfcTimingTest.java"
java -classpath "$BUILD" de.nethunter.flippernfc.NfcTimingTest
javac -encoding UTF-8 -classpath "$BASE/build/install/classes:$SDK/platforms/android-35/android.jar" -d "$BUILD" "$BASE/app/src/main/java/de/nethunter/flippernfc/NfcApdu.java" "$BASE/tests/NfcApduTest.java"
java -classpath "$BUILD:$BASE/build/install/classes:$SDK/platforms/android-35/android.jar" de.nethunter.flippernfc.NfcApduTest
