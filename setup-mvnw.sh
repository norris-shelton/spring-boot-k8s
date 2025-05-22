#!/bin/bash
# Script to create Maven wrapper files

mkdir -p .mvn/wrapper
curl -o .mvn/wrapper/maven-wrapper.jar https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
curl -o .mvn/wrapper/maven-wrapper.properties https://raw.githubusercontent.com/apache/maven-wrapper/master/src/main/resources/default-wrapper.properties

cat > mvnw << 'EOF'
#!/bin/sh
exec java -jar "$(dirname "$0")"/.mvn/wrapper/maven-wrapper.jar "$@"
EOF

chmod +x mvnw
