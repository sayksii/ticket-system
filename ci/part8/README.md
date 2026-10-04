# PART 8 CI/CD Add-on

這個資料夾是 `Jenkinsfile` 呼叫的 CI/CD Script。

## 檔案

```text
ci/part8/
├─ remote-build.sh
└─ k8s-release.sh

scripts/
└─ part8-smoke.sh
```

### `remote-build.sh`

執行位置：

```text
Harbor VM
10.10.10.20
```

用途：

```text
Docker Build
↓
Docker Push Harbor
```

參數：

```text
remote-build.sh \
  <REGISTRY> \
  <PROJECT> \
  <TAG> \
  <SERVICE>
```

`SERVICE`：

```text
ALL
backend
order-worker
frontend
```

### `k8s-release.sh`

執行位置：

```text
k3s-server
10.10.10.10
```

Action：

```text
deploy
= kubectl set image

rollout
= kubectl rollout status

show
= 顯示目前 Deployment Image
```

### `part8-smoke.sh`

執行位置：

```text
k3s-server
```

Backend / Frontend 走：

```text
10.10.10.10
↓ Host: ticket.local
Traefik
↓
Ingress
↓
Service
↓
Pod
```

所以不是用 Pod IP 當正式驗證入口。
