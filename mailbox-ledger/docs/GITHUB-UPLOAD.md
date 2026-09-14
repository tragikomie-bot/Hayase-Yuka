# 上传 GitHub

## 1. 创建仓库

建议仓库名：`mailbox-ledger`

建议仓库描述：

> 邮箱记账：支持多账本、日历收支、外币折算与系统深浅主题的安卓本地记账应用。

可以创建公开仓库。当前源码包已经包含 README 和 .gitignore，无需重复初始化这些文件。

## 2. 上传源码

解压 `mailbox-ledger-github.zip`，打开里面的 `mailbox-ledger` 文件夹。把这个文件夹里的文件和子文件夹上传到仓库根目录，让 README.md、build.sh、app/、tests/、docs/ 直接出现在仓库根目录。

不要只把整个 ZIP 上传为仓库中的一个文件，那样 GitHub 不会直接展示源码和 README。

若使用网页上传，通过 Add file → Upload files 选择文件，最后提交。若设备的文件选择器隐藏 .gitignore，可在仓库使用 Add file → Create new file 创建同名文件并复制源码包中的内容。不要在 GitHub 网页上传私人签名文件；网页上传不依赖本地 .gitignore 来保护文件。

## 3. 发布 APK

进入仓库的 Releases，新建一个 Release：

- 标签（Tag）：`v1.1.0`。标签不能为空；如果尚不存在，创建这个新标签。
- 标题：`邮箱记账 v1.1.0`。
- 说明：复制 `docs/RELEASE-v1.1.0.md` 的内容。
- 附件：上传单独提供的 `mailbox-ledger-1.1.0.apk`。

确认后发布。其他人便可从 Releases 下载 APK；GitHub 会根据该标签自动提供源码压缩包。

## 文件区分

- `mailbox-ledger-github.zip`：此次专门整理的公开源码包，解压后上传仓库。
- `mailbox-ledger-1.1.0.apk`：已签名安装包，上传到 Release 附件。
- 之前的 `mailbox-ledger-source.zip`：私人源码备份，含签名材料，不要公开上传。

## 构建说明

公开源码不包含作者签名私钥。自行执行 build.sh 会在本机生成自己的签名。要发布可以覆盖安装旧版的作者版本，需要在本机沿用原签名，而不能每次换一个新签名。

官方参考：

- https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository
- https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository
