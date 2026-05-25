{{/*
Helper templates for myapp-chart.
*/}}

{{/*
1.1 myapp.labels — standard labels applied to every resource metadata.labels block.
Includes helm.sh/chart (R3.7) and app.kubernetes.io/* standard labels.
*/}}
{{- define "myapp.labels" -}}
app: {{ .Release.Name }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
1.2 myapp.selectorLabels — stable selector labels for matchLabels / spec.selector.
Must NOT include helm.sh/chart or appVersion — those change on each release
and would cause immutable-field errors on Deployment upgrades.
*/}}
{{- define "myapp.selectorLabels" -}}
app: {{ .Release.Name }}
app.kubernetes.io/name: {{ .Release.Name }}
{{- end }}

{{/*
1.3 myapp.image — full image reference, handling optional registry prefix.
Values: image.registry (optional), image.repository, image.tag
*/}}
{{- define "myapp.image" -}}
{{- if .Values.image.registry -}}
{{ .Values.image.registry }}/{{ .Values.image.repository }}:{{ .Values.image.tag }}
{{- else -}}
{{ .Values.image.repository }}:{{ .Values.image.tag }}
{{- end -}}
{{- end }}
