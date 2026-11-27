#!/usr/bin/env bash
set -euo pipefail

classpath_file="${1:?Indica il file in cui salvare il classpath.}"
output_dir="${2:?Indica la cartella del Javadoc di verifica.}"

bash ./mvnw --batch-mode --no-transfer-progress dependency:build-classpath \
  -DincludeScope=compile "-Dmdep.outputFile=$classpath_file"
classpath="$(cat "$classpath_file")"
javadoc -quiet -classpath "$classpath" -d "$output_dir" '@scripts/javadoc-api.args'
