# AWS rekognition face liveness detection

[使用 AWS Rekognition 进行活体检测](https://aitsuki.com/blog/liveness-detection-with-aws-rekognition/)

## 运行项目

### 后端（Rust）

需要有AWS账户，然后并创建一个有用目标权限IAM用户：

1. AWS控制台>IAM>用户>创建用户，这里假设用户名为 rekognition-dev
2. 勾选策略 AmazonRekognitionFullAccess
3. 创建完成

创建accessKey

1. AWS控制台>IAM>用户>rekognition-dev
2. 选择安全凭证选项卡
3. 点击创建访问秘钥，进入到创建访问秘钥界面
4. 选择“其他”选项，然后点击下一步
5. 记录好 accessKeyId 和 secretAccessKey

配置accessKey到本地机器或服务器，可以通过aws amplify cli配置，也可以直接设置环境变量。这里演示使用临时环境变量的方式

进入 `server` 目录，执行以下命令：

windows(powershell):
```shell
$Env:AWS_ACCESS_KEY_ID="your access key id"
$Env:AWS_SECRET_ACCESS_KEY="your secret access key"
$Env:AWS_REGION="us-east-1"
cargo run
```

linux/macos:
```shell
export AWS_ACCESS_KEY_ID="your access key id"
export AWS_SECRET_ACCESS_KEY="your secret access key"
export AWS_REGION="us-east-1"
cargo run
```

### 安卓端（Compose）

使用android studio打开 `android` 目录，运行即可。

App启动时要求输入服务器域名，如果你是在本地电脑启动的服务器，且Android设备和你的电脑在同一网络中，可以输入电脑ip。
