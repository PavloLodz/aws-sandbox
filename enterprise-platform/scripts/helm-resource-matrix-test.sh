#!/usr/bin/env bash
# scripts/helm-resource-matrix-test.sh
#
# Verifies that conditional resources (Ingress, Route, ServiceMonitor) are
# present/absent correctly depending on which values file combination is used.
#
# IMPORTANT: stderr is NOT suppressed. If `helm` itself errors (missing binary,
# bad chart syntax, wrong working directory, etc.) you will see that error
# printed before the assertion failure — do not re-add 2>/dev/null here, it
# will hide the real cause and produce misleading "resource missing" reports.

# it should return something like this:
#=== Pre-flight: confirm helm is installed and chart is loadable ===
#v3.20.2+g8fb76d6
#✓ helm installed and chart lints cleanly
#
#=== Resource matrix assertions ===
#✓ Ingress present (args: -f infrastructure/helm/myapp-chart/values-k8s.yaml)
#✓ Ingress absent (args: -f infrastructure/helm/myapp-chart/values-openshift.yaml)
#✓ Route absent (args: -f infrastructure/helm/myapp-chart/values-k8s.yaml)
#✓ Route present (args: -f infrastructure/helm/myapp-chart/values-openshift.yaml)
#✓ ServiceMonitor absent (args: -f infrastructure/helm/myapp-chart/values-dev.yaml)
#✓ ServiceMonitor present (args: -f infrastructure/helm/myapp-chart/values-k8s.yaml -f infrastructure/helm/myapp-chart/values-staging.yaml)
#
#=== All resource matrix checks PASSED ===


set -euo pipefail
CHART=infrastructure/helm/myapp-chart

run_template() {
  # Captures both stdout and stderr together so Helm errors are visible,
  # but still lets us grep the YAML output for `kind:` lines.
  helm template myapp "$CHART" "$@"
}

assert_present() {
  local kind="$1"; shift
  local output
  if ! output=$(run_template "$@" 2>&1); then
    echo "✗ ERROR: helm template itself failed for kind=$kind, args=$*"
    echo "--- helm output ---"
    echo "$output"
    echo "-------------------"
    exit 1
  fi
  if ! echo "$output" | grep -q "^kind: $kind$"; then
    echo "✗ FAIL: expected $kind to be present with args: $*"
    exit 1
  fi
  echo "✓ $kind present (args: $*)"
}

assert_absent() {
  local kind="$1"; shift
  local output
  if ! output=$(run_template "$@" 2>&1); then
    echo "✗ ERROR: helm template itself failed for kind=$kind, args=$*"
    echo "--- helm output ---"
    echo "$output"
    echo "-------------------"
    exit 1
  fi
  if echo "$output" | grep -q "^kind: $kind$"; then
    echo "✗ FAIL: expected $kind to be ABSENT with args: $*"
    exit 1
  fi
  echo "✓ $kind absent (args: $*)"
}

echo "=== Pre-flight: confirm helm is installed and chart is loadable ==="
if ! command -v helm >/dev/null 2>&1; then
  echo "✗ ERROR: 'helm' is not installed or not on PATH."
  echo "  Install: https://helm.sh/docs/intro/install/"
  exit 1
fi
helm version --short
helm lint "$CHART" > /dev/null
echo "✓ helm installed and chart lints cleanly"
echo ""

echo "=== Resource matrix assertions ==="
assert_present Ingress        -f "$CHART/values-k8s.yaml"
assert_absent  Ingress        -f "$CHART/values-openshift.yaml"
assert_absent  Route          -f "$CHART/values-k8s.yaml"
assert_present Route          -f "$CHART/values-openshift.yaml"
assert_absent  ServiceMonitor -f "$CHART/values-dev.yaml"
assert_present ServiceMonitor -f "$CHART/values-k8s.yaml" -f "$CHART/values-staging.yaml"

echo ""
echo "=== All resource matrix checks PASSED ==="
