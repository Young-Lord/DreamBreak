每次修改代码后，运行以下命令编译并安装：

```
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
adb connect 192.168.31.136
adb install <build apk path>
```
