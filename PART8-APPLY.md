# PART 8 APPLY

這包直接疊加到目前已完成 PART 7 的同一個 Repository：

```text
D:\學習資源-碩\Docker_K8S\ticket-system-part1
```

會新增 / 覆蓋：

```text
Jenkinsfile

ci/
└─ part8/
   ├─ README.md
   ├─ remote-build.sh
   └─ k8s-release.sh

scripts/
└─ part8-smoke.sh

jenkins/
└─ Dockerfile.part8
```

`Dockerfile.part8` 是 Optional。

只有目前 Jenkins Image 缺：

```text
ssh
scp
ssh-keyscan
```

才需要 Build 新 Jenkins Image。

---

## 1. Windows 套 Add-on

把：

```text
ticket-system-part8-addon.zip
```

放到：

```text
D:\學習資源-碩\Docker_K8S
```

PowerShell：

```powershell
cd 'D:\學習資源-碩\Docker_K8S'

Expand-Archive `
  .\ticket-system-part8-addon.zip `
  -DestinationPath .\ticket-system-part1 `
  -Force
```

確認：

```powershell
cd .\ticket-system-part1
git status
```

應看到：

```text
Jenkinsfile
ci/part8/
scripts/part8-smoke.sh
jenkins/Dockerfile.part8
```

---

## 2. 先確認 Jenkins Image 有 SSH Client

在 `k3s-server`：

```bash
sudo kubectl exec \
  deployment/jenkins \
  -n devops-test \
  -- sh -c 'command -v ssh && command -v scp && command -v ssh-keyscan'
```

如果三個路徑都有顯示：

```text
不用重建 Jenkins Image
```

如果沒有，再做下面 Optional。

---

## 3. Optional：補 Jenkins SSH Client

先把新版 Repo Source 同步到 Harbor VM，或至少把：

```text
jenkins/Dockerfile.part8
```

傳進 Harbor VM。

在 Harbor VM、專案根目錄：

```bash
sudo docker build \
  -f jenkins/Dockerfile.part8 \
  -t 10.10.10.20:8858/devops-test/jenkins-maven:part8 \
  .
```

Push：

```bash
sudo docker push \
  10.10.10.20:8858/devops-test/jenkins-maven:part8
```

在 `k3s-server`：

```bash
sudo kubectl set image \
  deployment/jenkins \
  jenkins=10.10.10.20:8858/devops-test/jenkins-maven:part8 \
  -n devops-test
```

等：

```bash
sudo kubectl rollout status \
  deployment/jenkins \
  -n devops-test
```

---

## 4. Jenkins Plugin

Jenkins：

```text
Manage Jenkins
↓
Plugins
```

確認至少有：

```text
Pipeline
Git
Credentials Binding
SSH Agent
SonarQube Scanner
```

`SSH Agent` 很重要，因為 `Jenkinsfile` 使用：

```groovy
sshagent(...)
```

---

## 5. GitHub PAT

這份 Add-on 的 `Jenkinsfile` 固定使用：

```text
Credential ID = github-pat
```

所以這一版不論 Repo Public / Private，都先準備一個 GitHub Credential，流程最單純。

建議使用：

```text
Fine-grained PAT
```

至少要能：

```text
讀取這個 Ticket System Repository
Contents: Read
```

注意：

```text
之前如果 PAT 只授權 jenkins-maven-demo
不能直接拿來讀 ticket-system-part1
```

如果你的 Repo 是 Public、真的不想用 PAT：

```text
可以自行把 Jenkinsfile 的 github-pat Credential Checkout 改掉
```

但這份 Add-on 預設：

```text
github-pat 必須存在
```

Jenkins：

```text
Manage Jenkins
↓
Credentials
↓
System
↓
Global credentials
↓
Add Credentials
```

建立：

```text
Kind:
Username with password

Username:
<GITHUB_USER>

Password:
<GITHUB_PAT>

ID:
github-pat
```

---

## 6. Harbor Credential

Jenkins 新增：

```text
Kind:
Username with password

Username:
<HARBOR_USER>

Password:
<HARBOR_PASSWORD>

ID:
harbor-credential
```

Lab 可以先用目前帳號。

更正式可改：

```text
Harbor Robot Account
```

---

## 7. SonarQube Token

Windows Browser：

```text
http://10.10.10.10:30900
```

SonarQube：

```text
My Account
↓
Security
↓
Generate Token
```

Jenkins Credential：

```text
Kind:
Secret text

Secret:
<SONARQUBE_TOKEN>

ID:
sonarqube-token
```

---

## 8. Jenkins 設 SonarQube Server

Jenkins：

```text
Manage Jenkins
↓
System
↓
SonarQube servers
```

新增：

```text
Name:
sonarqube

Server URL:
http://sonarqube:9000

Server authentication token:
sonarqube-token
```

重點：

```text
Jenkins Pod
↓
Kubernetes Service DNS
↓
sonarqube:9000
```

不是走 NodePort 30900。

---

## 9. SonarQube → Jenkins Webhook

SonarQube：

```text
Administration
↓
Configuration
↓
Webhooks
↓
Create
```

Name：

```text
jenkins
```

URL：

```text
http://jenkins:8080/sonarqube-webhook/
```

這是：

```text
SonarQube Pod
↓
Jenkins Service
```

用來讓：

```text
waitForQualityGate
```

收到結果。

---

## 10. SSH Key

目前 Lab 可以沿用之前 Jenkins Publish Over SSH 的：

```text
~/jenkins-publish-key
~/jenkins-publish-key.pub
```

在 `k3s-server` 看 Public Key：

```bash
cat ~/jenkins-publish-key.pub
```

這把 Public Key 原本應該已在：

```text
k3s-server
~/.ssh/authorized_keys
```

現在把同一行 Public Key 也加到 Harbor VM：

```powershell
multipass shell harbor
```

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh

nano ~/.ssh/authorized_keys
```

貼上 Public Key。

再：

```bash
chmod 600 ~/.ssh/authorized_keys
```

---

## 11. Jenkins 建 `lab-ssh-key`

在 `k3s-server`：

```bash
cat ~/jenkins-publish-key
```

這是 Private Key。

只貼到自己的 Jenkins Credential。

不要：

```text
貼 GitHub
寫進 Jenkinsfile
寫進 README
```

Jenkins：

```text
Manage Jenkins
↓
Credentials
↓
System
↓
Global credentials
↓
Add Credentials
```

設定：

```text
Kind:
SSH Username with private key

Username:
ubuntu

Private Key:
Enter directly
→ 貼 Jenkins Private Key

ID:
lab-ssh-key
```

---

## 12. Harbor VM 準備 CI Build Root

Harbor VM：

```bash
mkdir -p ~/jenkins-builds
```

確認：

```bash
ls -ld ~/jenkins-builds
```

每次 Pipeline 會自己建立：

```text
/home/ubuntu/jenkins-builds/<job>-<build>/
```

Build 結束會清掉自己的暫存目錄。

---

## 13. K3s Server 準備 Deploy Root

在 `k3s-server`：

```bash
mkdir -p ~/jenkins-deploys
```

確認：

```bash
ls -ld ~/jenkins-deploys
```

每次 Pipeline：

```text
scp k8s-release.sh
scp part8-smoke.sh
↓
執行
↓
最後刪暫存目錄
```

---

## 14. 把 PART 8 檔案先 Push GitHub

Pipeline from SCM 要先從 GitHub 取得：

```text
Jenkinsfile
```

所以 Windows：

```powershell
cd 'D:\學習資源-碩\Docker_K8S\ticket-system-part1'

git status
git diff

git add .
git commit -m "part8 add jenkins cicd pipeline"
git push
```

---

## 15. Jenkins 建 Pipeline Job

Jenkins：

```text
New Item
↓
Name:
ticket-system-cicd

↓
Pipeline
↓
OK
```

Pipeline：

```text
Definition:
Pipeline script from SCM

SCM:
Git

Repository URL:
<YOUR_TICKET_SYSTEM_GITHUB_REPO_URL>

Credentials:
github-pat

Branch Specifier:
*/main

Script Path:
Jenkinsfile
```

Save。

---

## 16. 第一輪先手動跑

第一次可以按：

```text
Build Now
```

Jenkinsfile 載入後，之後會看到：

```text
Build with Parameters
```

參數：

```text
BRANCH
= main

IMAGE_TAG
= auto

SERVICE
= ALL
```

第一輪先不要開：

```text
Poll SCM
GitHub Webhook
```

先確認整條 Pipeline 自己是通的。

---

## 17. Pipeline 預期 Stage

```text
Init
↓
Checkout
↓
Test
↓
Package
↓
SonarQube - Backend
↓
Quality Gate - Backend
↓
SonarQube - Order Worker
↓
Quality Gate - Order Worker
↓
Prepare SSH
↓
Docker Build & Push
↓
Prepare Deploy
↓
Deploy
↓
Rollout Check
↓
Smoke Test
↓
Show Runtime Images
```

只要前面 Stage Fail：

```text
後面不應該繼續 Deploy
```

---

## 18. Image Tag

`IMAGE_TAG=auto`：

```text
BUILD_NUMBER-GIT_SHORT
```

例如：

```text
38-a3f8c21
```

所以 Harbor：

```text
ticket-backend:38-a3f8c21
ticket-order-worker:38-a3f8c21
ticket-frontend:38-a3f8c21
```

---

## 19. SERVICE

```text
ALL
= 三個 Application 都 Build / Push / Deploy

backend
= 只處理 ticket-backend

order-worker
= 只處理 ticket-order-worker

frontend
= 只處理 ticket-frontend
```

Java Test / SonarQube：

```text
第一版仍然會跑 Backend + Worker
```

目的是：

```text
先保證整個 Repo 沒被改壞
```

---

## 20. Branch 保護

目前：

```text
BRANCH=main
↓
可以 Deploy
```

如果：

```text
BRANCH != main
```

Pipeline 仍可以：

```text
Checkout
Test
Package
Sonar
Docker Build / Push
```

但會跳過：

```text
Deploy
Rollout
Smoke Test
```

避免 Feature Branch 直接改 Lab Runtime。

---

## 21. 第一次 Quality Gate 卡住

如果卡：

```text
Quality Gate
```

先確認 SonarQube Webhook：

```text
http://jenkins:8080/sonarqube-webhook/
```

不要填：

```text
http://10.10.10.10:30808/...
```

因為兩個 Service 都在 K8s 裡。

---

## 22. 第一個最小排錯方式

如果 Pipeline Fail：

```text
只看第一個紅色 Stage
```

例如：

```text
Checkout ❌
→ GitHub / PAT

Sonar ❌
→ Sonar URL / Token

Quality Gate timeout ❌
→ SonarQube Webhook

Docker Build & Push ❌
→ SSH / Docker / Harbor

Deploy ❌
→ SSH / kubectl / Deployment name

Rollout Check ❌
→ 新 Pod Runtime

Smoke Test ❌
→ Ingress / Service / Application
```

---

## 23. Pipeline 成功後再開 Poll SCM

因為目前 Jenkins：

```text
10.10.10.10:30808
```

是內網。

GitHub.com 不能直接打進：

```text
10.10.10.10
```

所以本機學習環境先：

```text
Poll SCM
```

Jenkins Job：

```text
Configure
↓
Build Triggers
↓
Poll SCM
```

Schedule：

```text
H/2 * * * *
```

意思：

```text
大約每 2 分鐘檢查 GitHub
有新 Commit 才 Build
```

不是：

```text
每 2 分鐘固定 Build
```

---

## 24. 最後驗證

Windows 改：

```text
frontend/index.html
```

改一行無害文字。

然後：

```powershell
git add .
git commit -m "part8 verify cicd"
git push
```

如果 Poll SCM 已開：

```text
GitHub
↓
Jenkins 發現新 Commit
↓
Pipeline
↓
Harbor 新 Tag
↓
K8s Rolling Update
↓
Smoke Test
```

最後：

```text
http://ticket.local/
```

看到新版內容。

---

## 25. 注意：不要做這些

不要把：

```text
GitHub PAT
Harbor Password
SonarQube Token
SSH Private Key
```

寫進：

```text
Jenkinsfile
Shell Script
Git
```

不要：

```text
Pipeline Fail
↓
就 multipass restart --all
```

先看：

```text
第一個 Fail Stage
```

也不要為了 Harbor 401：

```text
刪 /data
刪 DB
刪 Docker Volume
```

---

## 26. PART 8 核心完成標準

```text
git push
↓
Jenkins 自動發現
↓
Test
↓
SonarQube
↓
Quality Gate
↓
Docker Build
↓
Harbor Push
↓
K8s Deploy
↓
Rollout Success
↓
Smoke Test Success
```

做到這裡：

```text
PART 8 完成
```
