# FitMind 云服务器部署包

## 运行环境

- Linux x86_64
- Java 8 或更高版本
- MySQL 8
- Nginx

## 部署目录

将压缩包解压到 `/opt/fitmind`，最终结构：

```text
/opt/fitmind
├─ backend/fitmind-backend.jar
├─ frontend/
└─ config/
```

## 1. 准备数据库

公开仓库和部署包不包含数据库 SQL 文件。请在服务器上通过私有迁移流程准备 `fitmind` 数据库及所需表结构，不要把数据库结构、导出数据或初始化脚本上传到公开仓库。

## 2. 创建后端运行用户

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin fitmind
sudo chown -R fitmind:fitmind /opt/fitmind
```

## 3. 注册后端服务

复制环境变量示例并填写服务器自己的数据库和模型配置。不要把填写后的文件提交到 Git：

```bash
cp /opt/fitmind/config/fitmind.env.example /opt/fitmind/config/fitmind.env
chmod 600 /opt/fitmind/config/fitmind.env
```

```bash
sudo cp /opt/fitmind/config/fitmind-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now fitmind-backend
sudo systemctl status fitmind-backend
```

验证后端：

```bash
curl http://127.0.0.1:8080/api/dashboard/overview
```

## 4. 配置 Nginx

Ubuntu / Debian：

```bash
sudo cp /opt/fitmind/config/nginx-fitmind.conf /etc/nginx/sites-available/fitmind
sudo ln -s /etc/nginx/sites-available/fitmind /etc/nginx/sites-enabled/fitmind
sudo nginx -t
sudo systemctl reload nginx
```

CentOS / Rocky Linux 可将配置复制到 `/etc/nginx/conf.d/fitmind.conf`。

## 5. 防火墙

对公网开放 80 端口即可。8080 只供本机 Nginx 反向代理，不建议直接开放。

## 常用命令

```bash
sudo journalctl -u fitmind-backend -f
sudo systemctl restart fitmind-backend
sudo nginx -t
sudo systemctl reload nginx
```

## 安全提醒

公开仓库和构建产物不包含 API Key。生产环境使用权限为 `600` 的环境文件或云平台密钥管理服务注入配置，不要把填写后的 `fitmind.env` 上传到仓库、网盘或聊天工具。
