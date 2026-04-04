# Hetzner Cloud — Szerver beállítás és Deploy útmutató

**Dátum:** 2026-03-10
**Szerver:** `dronterapia-prod` — 46.225.12.228
**Domain:** https://app.dronterapia.hu

---

## Áttekintés

Ez a dokumentum leírja, hogyan kerül a DrónTerápia alkalmazás egy felhőszerverre, hogy bárki elérhesse az interneten keresztül. A folyamat lépései:

1. Szerver bérlése (Hetzner Cloud)
2. Alapbeállítások (tűzfal, SSH, Docker)
3. Kód felrakása GitHubról
4. App elindítása Docker containerkben
5. Domain és SSL tanúsítvány beállítása

---

## 1. Szerver megrendelése (Hetzner Cloud)

**URL:** https://console.hetzner.cloud

### Kiválasztott csomag

| Beállítás | Érték | Miért? |
|-----------|-------|--------|
| Típus | Cost Optimized x86 | ARM64 helyett x86, mert az ONNX és Ultralytics könyvtárak x86-ra optimalizáltak |
| Csomag | CX33 | 4 vCPU, 8 GB RAM, 80 GB SSD — elegendő az app + ML modellek futtatásához |
| Helyszín | Nuremberg (NBG1) | CX33 csak itt volt elérhető |
| OS | Ubuntu 24.04 LTS | Stabil, jól támogatott Linux |
| Ár | ~€5.49/hó | |

### SSH kulcs generálása (Windows PowerShell)

Az SSH kulcs helyettesíti a jelszót — biztonságosabb és kényelmesebb.

```powershell
ssh-keygen -t ed25519 -C "dronterapia-hetzner"
cat ~\.ssh\id_ed25519.pub
```

A kiírt `ssh-ed25519 AAAA...` sort fel kell tölteni a Hetzner rendelési felületén.

---

## 2. Első belépés a szerverre

```powershell
ssh root@46.225.12.228
```

Első csatlakozáskor megerősítést kér — írj `yes`-t.

---

## 3. Rendszer frissítés és Docker telepítés

```bash
apt update && apt upgrade -y && apt install -y curl git ufw
curl -fsSL https://get.docker.com | sh
systemctl enable docker && systemctl start docker
```

**Mi történik itt?**
- `apt update/upgrade` — rendszerfrissítések
- `curl git ufw` — segédprogramok (letöltő, verziókezelő, tűzfal)
- Docker telepítő script — a Docker az az eszköz, ami "dobozba csomagolja" az alkalmazást

---

## 4. Tűzfal beállítása

```bash
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

**Mi történik itt?**
Csak a szükséges portok nyílnak meg a külvilág felé:
- **22 (SSH)** — szerver kezeléshez
- **80 (HTTP)** — webes forgalom (átirányítja HTTPS-re)
- **443 (HTTPS)** — titkosított webes forgalom

Minden más port zárva marad.

---

## 5. GitHub deploy kulcs

A szerver tud "olvasni" a privát GitHub repóból egy deploy kulcson keresztül.

**Kulcs generálása a szerveren:**
```bash
ssh-keygen -t ed25519 -C "dronterapia-prod-server" -f ~/.ssh/id_ed25519 -N ""
cat ~/.ssh/id_ed25519.pub
```

**Hozzáadás GitHubhoz:**
`https://github.com/CsabaZsee77/Dronterapia/settings/keys/new`
- Title: `dronterapia-prod-server`
- Key: a kiírt `ssh-ed25519 AAAA...` sor
- Allow write access: ❌ (csak olvasás kell)

**Kapcsolat tesztelése:**
```bash
ssh -T git@github.com
# Várt válasz: Hi CsabaZsee77/Dronterapia! You've successfully authenticated...
```

---

## 6. Repo klónozása

```bash
git clone git@github.com:CsabaZsee77/Dronterapia.git /opt/dronterapia
```

A kód a `/opt/dronterapia` mappába kerül.

---

## 7. .env fájl létrehozása

A jelszavak és API kulcsok nem kerülnek GitHubra — ezeket kézzel kell létrehozni a szerveren.

```bash
cat > /opt/dronterapia/.env << 'EOF'
SMTP_USERNAME=...
SMTP_PASSWORD=...
EMAIL_ENABLED=true
CDSE_USER=...
CDSE_PASSWORD=...
EOF

chmod 600 /opt/dronterapia/.env
```

A `chmod 600` beállítja, hogy csak root olvashassa a fájlt.

---

## 8. Docker indítás

```bash
cd /opt/dronterapia
docker compose up --build -d
```

**Mi történik itt?**
- `--build` — felépíti az image-et (Python + könyvtárak telepítése, ~5-10 perc)
- `-d` — háttérben futtatja (detached mode)

**Containerek:**
| Container | Feladata |
|-----------|----------|
| `dronterapia_app` | Streamlit alkalmazás (8501-es porton, csak belsőleg) |
| `dronterapia_nginx` | Webszerver — fogadja a külső kéréseket és továbbítja az appnak |

---

## 9. Domain és SSL beállítás

### DNS rekord (Mediacenternél)

| Típus | Név | Cél IP |
|-------|-----|--------|
| A | app | 46.225.12.228 |

Propagálódás: 5-15 perc. Ellenőrzés:
```powershell
nslookup app.dronterapia.hu
```

### SSL tanúsítvány (Let's Encrypt — ingyenes)

**1. Ideiglenes nginx config (HTTP, certbot challenge-hez):**
```bash
# Csak HTTP-t fogad, a certbot ellenőrzési fájlját szolgálja ki
cat > /opt/dronterapia/nginx/nginx.conf << 'EOF'
server {
    listen 80;
    server_name app.dronterapia.hu;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}
EOF
mkdir -p /opt/dronterapia/certbot/www
docker compose restart nginx
```

**2. Certbot futtatása:**
```bash
docker run --rm \
  -v /opt/dronterapia/certbot/www:/var/www/certbot \
  -v /opt/dronterapia/certbot/conf:/etc/letsencrypt \
  certbot/certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email zsigmond.csaba@logpilot.hu \
  --agree-tos --no-eff-email \
  -d app.dronterapia.hu
```

A tanúsítvány 90 napig érvényes, helye: `/opt/dronterapia/certbot/conf/live/app.dronterapia.hu/`

**3. Végleges nginx config (HTTPS):**
```bash
cat > /opt/dronterapia/nginx/nginx.conf << 'EOF'
server {
    listen 80;
    server_name app.dronterapia.hu;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}
server {
    listen 443 ssl;
    http2 on;
    server_name app.dronterapia.hu;
    ssl_certificate /etc/letsencrypt/live/app.dronterapia.hu/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/app.dronterapia.hu/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    add_header Strict-Transport-Security "max-age=31536000" always;
    client_max_body_size 500M;
    location / {
        proxy_pass http://app:8501;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 300s;
    }
}
EOF
docker compose down && docker compose up -d
```

---

## 10. Deploy workflow (napi használat)

### Kódmódosítás → éles szerver

```
1. Helyi fejlesztés (saját gép, Streamlit lokálisan)
2. git push → GitHub
3. Szerveren: git pull + docker compose up --build
```

**Szerveres frissítés:**
```bash
cd /opt/dronterapia && git pull origin main && docker compose up --build -d
```

### Deploy script

```bash
cat > /opt/dronterapia/deploy.sh << 'EOF'
#!/bin/bash
cd /opt/dronterapia
git pull origin main
docker compose up --build -d
echo "Deploy kész: $(date)"
EOF
chmod +x /opt/dronterapia/deploy.sh
```

Ezután: `./deploy.sh`

---

## Fontos fájlok a szerveren

| Fájl/Mappa | Tartalom |
|------------|----------|
| `/opt/dronterapia/` | A teljes alkalmazás (git repo) |
| `/opt/dronterapia/.env` | Jelszavak, API kulcsok (NEM gitben) |
| `/opt/dronterapia/nginx/nginx.conf` | Webszerver konfiguráció |
| `/opt/dronterapia/certbot/conf/` | SSL tanúsítványok |
| `/opt/dronterapia/data/` | Felhasználói adatok, feltöltött képek |

---

## Hasznos parancsok

```bash
# Containerek állapota
docker compose ps

# App logok (utolsó 50 sor)
docker compose logs app --tail=50

# Nginx logok
docker compose logs nginx --tail=50

# Újraindítás kód nélkül
docker compose restart

# Teljes rebuild
docker compose up --build -d

# SSH belépés
ssh root@46.225.12.228
```

---

## SSL megújítás (90 naponta)

```bash
docker run --rm \
  -v /opt/dronterapia/certbot/www:/var/www/certbot \
  -v /opt/dronterapia/certbot/conf:/etc/letsencrypt \
  certbot/certbot renew

docker compose restart nginx
```

---

## Architektúra áttekintés

```
Internet
   │
   ▼
nginx (80/443)
   │  HTTPS proxy
   ▼
Streamlit app (8501, csak belső)
   │
   ├── data/ (volume — megmarad deploy után)
   └── models/ (volume — megmarad deploy után)
```

A `data/` és `models/` mappák Docker volume-ként vannak csatolva — ezek tartalma **nem törlődik** újrabuildeléskor.
