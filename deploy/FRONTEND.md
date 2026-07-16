# Frontend - what you need to know (single machine, IP only, HTTP)

Everything runs on one VM: **192.168.1.203**. Front, backend and DB are all here. No domain name,
no HTTPS. The rule that makes auth/cookies/CORS "just work": **the browser only talks to the
frontend on port 80**, and the frontend's nginx proxies `/api` to the local backend. That makes the
app and the API the **same origin** - so there is no CORS, and the HttpOnly auth cookie works over
plain HTTP.

```
Browser ──► http://192.168.1.203/            (frontend nginx, :80)
                 ├─ /            -> Angular SPA (static files)
                 └─ /api, /actuator -> proxy to the backend at 127.0.0.1:8080
```

## 1. Angular config
- Set the API base URL to the **relative** path `/api` (NOT `http://192.168.1.203:8080`).
  Then every call is same-origin through nginx.
- Send credentials so the refresh cookie flows: HttpClient `withCredentials: true`
  (or `credentials: 'include'` for fetch). No CORS settings needed.

## 2. nginx.conf (serve SPA + proxy to the local backend)
```nginx
server {
    listen 80;
    client_max_body_size 100m;            # uploads (max file 100MB)

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location /actuator/health {
        proxy_pass http://127.0.0.1:8080;
    }

    location / {                          # Angular SPA fallback
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
}
```

## 3. Run the frontend with host networking
Host networking lets nginx reach the backend on `127.0.0.1:8080` directly - no docker bridge,
no firewall rules, no service names. With your existing `deploy.sh`:

```bash
HOST_NET=1 ./deploy.sh
```
(Under `--network host`, nginx binds the host's port 80 and `proxy_pass http://127.0.0.1:8080`
reaches the backend's published port on the same machine.)

## 4. Use it
Open **http://192.168.1.203/**. Login and all `/api` calls go through nginx to the backend.

## Why this avoids the problems you hit
- **502 Bad Gateway** earlier = the frontend container couldn't reach the backend across the docker
  bridge. Host networking + `127.0.0.1:8080` removes that entirely.
- **CORS / cookies**: same origin (everything on `:80`), so no CORS config and the HttpOnly cookie
  is first-party - works on plain HTTP, no HTTPS needed for this test setup.
- Only port **80** needs to be open in the firewall.
