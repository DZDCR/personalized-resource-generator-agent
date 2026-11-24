#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "Compiling backend and resolving runtime classpath..."
mvn -q -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target/cp.txt" "-Dmdep.includeScope=runtime"

CP="target/classes:$(cat target/cp.txt)"
echo "Starting learning-agent-system on http://localhost:8080 ..."
java -cp "$CP" com.example.learning.LearningApplication
