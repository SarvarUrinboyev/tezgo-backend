@echo off
set MVN=C:\Users\Laptop\.m2\wrapper\dists\apache-maven-3.9.12\59fe215c0ad6947fea90184bf7add084544567b927287592651fda3782e0e798\bin\mvn.cmd

echo [TezYol] Server ishga tushmoqda...
"%MVN%" spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"
