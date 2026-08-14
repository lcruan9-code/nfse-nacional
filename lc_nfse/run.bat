@echo off
setlocal
set "JDK=C:\Program Files\Java\jdk-17"
set "LIB=C:\LC sistemas - Softhouse - 321\lib"
set "CP=lc_nfse.jar;%LIB%\CampoTexto.jar;%LIB%\CampoValorNumerico.jar;%LIB%\mysql-connector-java-5.1.36-bin.jar;%LIB%\iText-2.1.7.jar;%LIB%\core-2.3.0.jar;%LIB%\javase-2.2.jar"
"%JDK%\bin\java" -cp "%CP%" br.com.lc.nfse.tela.Main
endlocal
