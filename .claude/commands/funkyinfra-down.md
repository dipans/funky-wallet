Tear down the FunkyWallet Helm release from the Kubernetes cluster.

## Steps

### 1. Helm uninstall
```bash
helm uninstall funky-wallet --namespace funky-wallet
```

### 2. Optionally delete the namespace (removes ALL resources including PVCs)
Only do this if the user explicitly asks to wipe storage too:
```bash
kubectl delete namespace funky-wallet
```

### 3. Verify everything is removed
```bash
kubectl get all -n funky-wallet 2>/dev/null || echo "Namespace gone"
helm list --namespace funky-wallet
```

### 4. Report what was removed
List the resources that were deleted and confirm no pods remain in the `funky-wallet` namespace.

Note: PersistentVolumeClaims (postgres-pvc) survive `helm uninstall` by default. Warn the user if they need the data wiped — they must delete the PVC manually or delete the namespace.
