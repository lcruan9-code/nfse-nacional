@echo off
setlocal
set "JDK=C:\Program Files\Java\jdk-17"
set "LIB=C:\LC sistemas - Softhouse - 321\lib"
set "CP=%LIB%\CampoTexto.jar;%LIB%\CampoValorNumerico.jar;%LIB%\mysql-connector-java-5.1.36-bin.jar;%LIB%\iText-2.1.7.jar"

if exist out rmdir /s /q out
mkdir out
"%JDK%\bin\javac" -encoding UTF-8 -cp "%CP%" -d out src\br\com\lc\nfse\tela\*.java
if errorlevel 1 (echo ERRO na compilacao & exit /b 1)
"%JDK%\bin\jar" cfe lc_nfse.jar br.com.lc.nfse.tela.Main -C out .
echo.
echo lc_nfse.jar gerado com sucesso.
endlocal
