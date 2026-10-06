{{/* Gemeenschappelijke labels; Services en PDB's selecteren op naam + instance. */}}
{{- define "banksim.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: banksim
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
{{- end }}

{{- define "banksim.selector" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{- define "banksim.image" -}}
{{ index .root.Values.images .key }}:{{ .root.Values.imageTag }}
{{- end }}

{{/* Pod-securitycontext volgens Pod Security Standard "restricted". */}}
{{- define "banksim.podSecurity" -}}
automountServiceAccountToken: false
securityContext:
  runAsNonRoot: true
  runAsUser: {{ .uid }}
  runAsGroup: {{ .uid }}
  fsGroup: {{ .uid }}
  seccompProfile:
    type: RuntimeDefault
{{- end }}

{{- define "banksim.containerSecurity" -}}
securityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: {{ if hasKey . "readOnly" }}{{ .readOnly }}{{ else }}true{{ end }}
  capabilities:
    drop: ["ALL"]
{{- end }}

{{/* Certificaat van een component, gemaakt door deploy/scripts/certs.sh. */}}
{{- define "banksim.tlsVolume" -}}
- name: tls
  secret:
    secretName: tls-{{ .name }}
    defaultMode: {{ .mode | default 0440 }}
{{- end }}

{{/* JVM-brede key- en truststore: elke uitgaande HTTPS-call toont het clientcertificaat en vertrouwt alleen de BankSim-CA. */}}
{{- define "banksim.javaTls" -}}
- name: KEYSTORE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: tls-{{ .name }}
      key: keystore-password
- name: JAVA_TOOL_OPTIONS
  value: >-
    -XX:MaxRAMPercentage=75
    -Djavax.net.ssl.keyStore=/etc/banksim/tls/keystore.p12
    -Djavax.net.ssl.keyStoreType=PKCS12
    -Djavax.net.ssl.keyStorePassword=$(KEYSTORE_PASSWORD)
    -Djavax.net.ssl.trustStore=/etc/banksim/tls/truststore.p12
    -Djavax.net.ssl.trustStoreType=PKCS12
    -Djavax.net.ssl.trustStorePassword=$(KEYSTORE_PASSWORD)
{{- end }}

{{/* Probes van een Spring Boot-service op de management-poort. */}}
{{- define "banksim.springProbes" -}}
startupProbe:
  httpGet: { path: /actuator/health/startup, port: management }
  periodSeconds: 5
  failureThreshold: 120
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: management }
  periodSeconds: 10
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: management }
  periodSeconds: 5
{{- end }}

{{/* Bij nieuwe certificaten verandert de checksum, zodat pods opnieuw starten en de nieuwe certificaten laden. */}}
{{- define "banksim.podAnnotations" -}}
annotations:
  banksim.nl/certs: {{ .Values.certsChecksum | default "none" | quote }}
{{- end }}
