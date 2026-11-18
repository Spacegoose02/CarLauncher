# Car Launcher config server

A small self-hosted app for making dashboard layouts and colour themes in a web page. The launcher fetches
them from here, so you design on a laptop and the car picks it up.

It is one Node.js file with no dependencies, packaged for Docker. Layouts and themes are stored as plain
JSON files in `/data` (one file each), so they are easy to back up or edit by hand.

## Run it

With Docker Compose, from this folder:

```bash
docker compose up -d
```

Or with the ready-made image (built for PCs, NAS boxes and Raspberry Pi):

```bash
docker run -d --name carlauncher-config --restart unless-stopped \
  -p 8080:8080 -v carlauncher-config:/data \
  ghcr.io/spacegoose02/carlauncher-config:latest
```

Then open `http://<your server's address>:8080` in a browser.

### Unraid

1. Open the Unraid **Terminal** (the `>_` icon at the top right) and paste:

   ```bash
   wget -O /boot/config/plugins/dockerMan/templates-user/my-carlauncher-config.xml \
     https://raw.githubusercontent.com/Spacegoose02/CarLauncher/main/server/unraid/carlauncher-config.xml
   ```

2. Go to **Docker → Add Container**, and pick **carlauncher-config** from the **Template** list.
3. Leave the defaults (port 8080, data in `/mnt/user/appdata/carlauncher-config`), optionally set an
   **Admin token**, and click **Apply**.
4. Click the container's icon, then **WebUI**, to open the editor.

On first start it fills itself with the launcher's three built-in layouts (map right, map left, no map)
and three themes, to start from.

| Setting | Default | |
|---|---|---|
| `PORT` | `8080` | Port inside the container. |
| `DATA_DIR` | `/data` | Where the JSON files live. Mount a volume here. |
| `PUID` / `PGID` | `1000` / `1000` | User and group that own `/data` and run the server (Unraid: `99` / `100`). |
| `ADMIN_TOKEN` | empty | When set, the editor asks for this token before saving or deleting. The launcher only reads, so it never needs it. |

## In the launcher

**Settings → Config server**: type the server's address (for example `192.168.1.20:8080`) and tap
**Save and sync**. Your layouts then appear under **Layout**, and themes under **Look → Colours**.

Changes go to the car by themselves. While the dashboard is on screen, the launcher keeps one quiet request open
to the server, and the server answers it the moment you save (or edit a file in `/data` by hand), so the car
updates within a second or two. The editor's header shows when a car is connected. With no network the launcher
keeps using its last copy and reconnects when it can. **Sync now** is still there if you want it.

The tablet has to reach the server. On home Wi-Fi a local address works. To sync away from home, put
the server behind something like Tailscale or a reverse proxy with HTTPS.

## Sharing layouts and themes

- **Share** (on any layout or theme) gives you a share code, a line of text starting with `CL1.`. Send it in a
  message; the other person pastes it into **Import** on their own server. **Copy link** gives a link that
  opens this editor with the import ready, and **Download file** saves it as JSON.
- **Import** takes a share code, a JSON file, or a whole backup. If a name is already taken you choose to
  keep both, replace yours, or keep yours. Things you already have are never duplicated.
- **Export all** downloads every layout and theme as one file, as a backup or to move to another server.

## Modules

The editor lists the modules this server knows: `CLOCK`, `SPEEDOMETER`, `MEDIA`, `WAVELENGTH_WIDGET`, `MAP`
(one per layout), `WEATHER` and `SPORTS`. A layout may also use a module name the server doesn't list yet
(**+ module → Other…**), for a newer launcher; a launcher that doesn't have that module skips the layout.

To add or relabel modules without rebuilding the image, put a `modules.json` in the data folder:

```json
[ { "type": "OBD_GAUGE", "label": "Engine gauges", "description": "RPM, coolant, fuel", "maxPerLayout": 2 } ]
```

## Formats

A **layout** is columns side by side, each a stack of rows, each row one to four modules. `weight` is a
share of the space (a column at 0.6 next to one at 0.4 gets 60%). The dock always runs along the bottom.
See **Modules** above for the module names.

```json
{
  "id": "map_right",
  "name": "Map on the right",
  "columns": [
    { "weight": 0.42, "rows": [
      { "weight": 1.0, "widgets": ["CLOCK", "SPEEDOMETER"] },
      { "weight": 1.4, "widgets": ["MEDIA"] }
    ] },
    { "weight": 0.58, "rows": [ { "widgets": ["MAP"] } ] }
  ]
}
```

A layout with the same `id` as a built-in one (`map_right`, `map_left`, `no_map`) replaces it in the launcher.

A **theme** is a set of colours:

```json
{
  "id": "synthwave",
  "name": "Synthwave",
  "dark": true,
  "colors": {
    "accent": "#FF2BD6", "onAccent": "#000000",
    "background": "#120724", "surface": "#1E0F3A", "surfaceVariant": "#2C1652",
    "text": "#F8E9FF", "textSecondary": "#B9A2D8"
  }
}
```

## API

| | |
|---|---|
| `GET /api/config` | Everything the launcher needs: `{ formatVersion, revision, layouts, themes }`, with the revision as the `ETag` |
| `GET /api/config?wait=50` + `If-None-Match: "<revision>"` | Waits up to 50 s (60 at most) for a change: `200` with the new config, or `304` if nothing changed. `?since=<revision>` works too |
| `GET /api/status` | `{ revision, listening, lastSync }`: how many launchers are waiting for changes, and when one last synced |
| `GET /api/export[?layouts=a,b&themes=c][&download]` | A bundle file of everything, or of the ones named |
| `GET /api/share?layouts=a&themes=b` | `{ code }`: a `CL1.` share code for the ones named |
| `POST /api/import[?conflict=copy\|replace\|skip]` | Body: a share code, a bundle, one layout or theme, or a list. Returns what was added, replaced, unchanged, skipped or failed |
| `GET /api/layouts`, `GET /api/layouts/{id}` | List or read layouts |
| `PUT /api/layouts/{id}` | Create or replace (validated; errors come back as a list) |
| `DELETE /api/layouts/{id}` | Delete |
| `…/api/themes…` | The same for themes |
| `GET /api/meta` | The module catalog and colour names, for the editor |

`PUT`, `DELETE` and `POST /api/import` need `Authorization: Bearer <ADMIN_TOKEN>` when a token is set.

## Develop

```bash
npm start      # serves on :8080, data in ./data
npm test
```
