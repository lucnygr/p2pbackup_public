@echo off
echo Enter username (alias):
set /p USERNAME=

echo Enter Password for KeyStore:
set /p PASSWORD=

echo Repeat Password for KeyStore:
set /p PASSWORD_REPEAT=

IF NOT "%PASSWORD%" == "%PASSWORD_REPEAT%" (
    echo Passwords do not match
    goto END
)

echo Generating KeyPair
"%JAVA_HOME%/bin/keytool" -genkeypair -alias %USERNAME% -keyalg RSA -keysize 3072 -keystore %USERNAME%.pfx -storepass %PASSWORD% -storetype pkcs12

echo Export certificate
"%JAVA_HOME%/bin/keytool" -exportcert -alias %USERNAME% -keystore %USERNAME%.pfx -storepass %PASSWORD% -rfc -file %USERNAME%.cer

REM echo Create TrustStore
REM "%JAVA_HOME%/bin/keytool" -importcert -alias %USERNAME% -file %USERNAME%.cer -keystore truststore.pfx -storepass %PASSWORD%

:END
pause