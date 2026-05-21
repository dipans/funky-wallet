{{/*
Expand the name of the chart.
*/}}
{{- define "funky-wallet.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "funky-wallet.labels" -}}
app.kubernetes.io/managed-by: Helm
app.kubernetes.io/part-of: funky-wallet
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}

{{/*
Selector labels for a given service.
Usage: include "funky-wallet.selectorLabels" (dict "name" $name)
*/}}
{{- define "funky-wallet.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: funky-wallet
{{- end }}
