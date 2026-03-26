每次修改代码后，运行以下命令编译并安装：

```
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
zsh -i -c "adb kde"
adb install <build apk path>
```
