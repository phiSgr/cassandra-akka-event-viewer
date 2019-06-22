# Cassandra Akka Event Viewer

The tool allows viewing and editing messages and snapshots persisted by
[akka-persistence-cassandra](https://github.com/akka/akka-persistence-cassandra).

## Java Version

TornadoFX requires Java 8, and some distributions do not include JavaFX.
Luckily a JDK 8 with JavaFX can be downloaded
from [Azul](https://www.azul.com/downloads/zulu/zulufx/).

```bash
$ export JAVA_HOME=~/Downloads/zulu8.38.0.13-ca-fx-jdk8.0.212-macosx_x64/
$ java -version
openjdk version "1.8.0_212"
OpenJDK Runtime Environment (Zulu 8.38.0.13-CA-macosx) (build 1.8.0_212-b04)
OpenJDK 64-Bit Server VM (Zulu 8.38.0.13-CA-macosx) (build 25.212-b04, mixed mode)
```

`start_osx.sh` sets the `JAVA_HOME` before calling `./gradlew run`.

## Use

This is a clone-modify-use program.
You will need to add the protobuf files into `src/main/proto`,
and modify the `application.conf` to specify the message classes.

You may not need to modify the cassandra `contactPoints`,
if you can port forward the 9042 port from the cluster.

## Modification and deletion

After modification or deletion,
the list in the main view
is **NOT** modified.
This is by design, the original data is retained in memory,
and can be restored by opening the modal and saving again.

If you want to see them updated, fetch the list of data again.
