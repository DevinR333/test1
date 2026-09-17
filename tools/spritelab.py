#!/usr/bin/env python3
"""Sprite editor for the Oracle disassembly's graphics.

Run it and it opens in your browser:

    python3 tools/spritelab.py

It finds the disassembly, lists every sprite sheet, and lets you edit tiles
with the four-colour palette the hardware uses. Saving writes the file back in
the same 2-bit indexed format the build expects, so `make seasons` picks up
the change.

Nothing is uploaded anywhere. The server binds to localhost, reads and writes
only inside the graphics directory, and your files never leave the machine.
"""

import argparse
import http.server
import io
import json
import os
import socket
import socketserver
import sys
import threading
import urllib.parse
import webbrowser
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tiles as tilelib

# Where a disassembly checkout usually sits, relative to the project root.
SEARCH_PATHS = [
    "external/oracles-disasm",
    "../oracles-disasm",
    "oracles-disasm",
]

GFX_SUBDIRS = ["gfx/common", "gfx/seasons", "gfx/ages", "gfx_compressible"]


def find_disasm(explicit=None):
    if explicit:
        p = Path(explicit).expanduser().resolve()
        if not (p / "gfx").is_dir():
            raise SystemExit(f"error: {p} does not look like the disassembly "
                             f"(no gfx/ directory)")
        return p
    root = Path(__file__).resolve().parent.parent
    for rel in SEARCH_PATHS:
        p = (root / rel).resolve()
        if (p / "gfx").is_dir():
            return p
    raise SystemExit(
        "error: could not find the disassembly.\n"
        "  Looked in: " + ", ".join(SEARCH_PATHS) + "\n"
        "  Pass it explicitly:  python3 tools/spritelab.py --dir PATH")


def list_sheets(disasm):
    """Every editable PNG, newest-looking name first within each directory."""
    out = []
    for sub in GFX_SUBDIRS:
        d = disasm / sub
        if not d.is_dir():
            continue
        for png in sorted(d.glob("*.png")):
            try:
                with open(png, "rb") as fh:
                    head = fh.read(33)
                import struct
                w, h, depth, ctype = struct.unpack(">IIBB", head[16:26])
            except Exception:
                continue
            if ctype not in (0, 3):
                continue
            out.append({
                "path": str(png.relative_to(disasm)).replace("\\", "/"),
                "name": png.stem,
                "dir": sub,
                "w": w, "h": h, "depth": depth,
                "tiles_x": w // 8, "tiles_y": h // 8,
            })
    return out


class Handler(http.server.SimpleHTTPRequestHandler):
    disasm = None
    sheets = []

    def log_message(self, *a):
        pass                                    # quiet; the page is the UI

    # -- safety ----------------------------------------------------------
    def _resolve(self, rel):
        """Resolve a client-supplied path, refusing anything outside gfx."""
        target = (self.disasm / rel).resolve()
        allowed = [(self.disasm / d).resolve() for d in GFX_SUBDIRS]
        if not any(str(target).startswith(str(a) + os.sep) for a in allowed):
            raise ValueError("path outside the graphics directories")
        if target.suffix.lower() != ".png":
            raise ValueError("not a PNG")
        return target

    def _json(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    # -- routes ----------------------------------------------------------
    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        if url.path == "/":
            body = PAGE.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if url.path == "/api/sheets":
            return self._json(self.sheets)

        if url.path == "/api/image":
            q = urllib.parse.parse_qs(url.query)
            try:
                target = self._resolve(q.get("path", [""])[0])
                pixels, palette, depth = tilelib.read_png(target)
            except Exception as exc:
                return self._json({"error": str(exc)}, 400)
            return self._json({
                "w": len(pixels[0]), "h": len(pixels), "depth": depth,
                "palette": [list(c[:3]) for c in palette],
                "pixels": [px for row in pixels for px in row],
            })

        self.send_error(404)

    def do_POST(self):
        url = urllib.parse.urlparse(self.path)
        if url.path != "/api/save":
            return self.send_error(404)

        length = int(self.headers.get("Content-Length", 0))
        try:
            payload = json.loads(self.rfile.read(length))
            target = self._resolve(payload["path"])
            w, h = int(payload["w"]), int(payload["h"])
            flat = payload["pixels"]
            if len(flat) != w * h:
                raise ValueError(f"expected {w * h} pixels, got {len(flat)}")
            pixels = [flat[y * w:(y + 1) * w] for y in range(h)]
            palette = [tuple(c) for c in payload["palette"]]
            depth = int(payload.get("depth", 2))

            # Write beside the original, then replace, so an interrupted save
            # cannot leave a truncated file the build would choke on.
            tmp = target.with_suffix(".png.tmp")
            tilelib.write_png_indexed(tmp, pixels, palette, depth)
            check, _, _ = tilelib.read_png(tmp)
            if check != pixels:
                tmp.unlink(missing_ok=True)
                raise ValueError("verification failed; the file was not written")
            os.replace(tmp, target)
        except Exception as exc:
            return self._json({"error": str(exc)}, 400)

        return self._json({"ok": True, "path": payload["path"]})


PAGE = r"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Sprite Lab</title>
<style>
  :root{
    --bg:#f6f6f4; --panel:#fff; --ink:#1a1a18; --muted:#6b6b66;
    --line:#dededa; --accent:#3b6ea5; --accent-ink:#fff;
  }
  @media (prefers-color-scheme: dark){ :root:not([data-theme=light]){
    --bg:#16161a; --panel:#1f1f25; --ink:#eceCf0; --muted:#9a9aa4;
    --line:#32323a; --accent:#5b9dd9; --accent-ink:#0d0d10;
  }}
  *{box-sizing:border-box}
  body{margin:0;background:var(--bg);color:var(--ink);
    font:14px/1.5 ui-sans-serif,system-ui,-apple-system,Segoe UI,sans-serif;
    display:grid;grid-template-columns:260px 1fr;height:100vh;overflow:hidden}
  @media (max-width:860px){body{grid-template-columns:1fr;overflow:auto;height:auto}}
  aside{background:var(--panel);border-right:1px solid var(--line);
    overflow-y:auto;padding:16px}
  h1{font-size:15px;margin:0 0 4px;letter-spacing:-.01em}
  .sub{color:var(--muted);font-size:12px;margin-bottom:16px}
  .group{font-size:11px;text-transform:uppercase;letter-spacing:.06em;
    color:var(--muted);margin:16px 0 6px}
  .item{padding:6px 8px;border-radius:6px;cursor:pointer;font-size:13px;
    display:flex;justify-content:space-between;gap:8px}
  .item:hover{background:var(--bg)}
  .item.on{background:var(--accent);color:var(--accent-ink)}
  .item small{opacity:.7;font-variant-numeric:tabular-nums}
  main{padding:16px;overflow:auto;display:flex;flex-direction:column;gap:12px}
  .bar{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
  button{font:inherit;padding:6px 12px;border:1px solid var(--line);
    background:var(--panel);color:var(--ink);border-radius:6px;cursor:pointer}
  button:hover{border-color:var(--accent)}
  button.primary{background:var(--accent);color:var(--accent-ink);border-color:var(--accent)}
  button:disabled{opacity:.45;cursor:default}
  .swatches{display:flex;gap:6px}
  .sw{width:30px;height:30px;border-radius:6px;border:2px solid var(--line);cursor:pointer}
  .sw.on{border-color:var(--ink);box-shadow:0 0 0 2px var(--bg),0 0 0 4px var(--ink)}
  canvas{image-rendering:pixelated;background:
    repeating-conic-gradient(#8884 0 25%,#0000 0 50%) 0 0/16px 16px;
    border:1px solid var(--line);border-radius:4px;cursor:crosshair;touch-action:none}
  .status{color:var(--muted);font-size:12px;min-height:18px}
  .empty{color:var(--muted);padding:40px;text-align:center}
</style></head><body>
<aside>
  <h1>Sprite Lab</h1>
  <div class="sub" id="root">loading…</div>
  <div id="list"></div>
</aside>
<main>
  <div class="bar">
    <button id="zout">&minus;</button>
    <span id="zoom" style="min-width:52px;text-align:center">8&times;</span>
    <button id="zin">+</button>
    <span style="width:12px"></span>
    <div class="swatches" id="sw"></div>
    <span style="width:12px"></span>
    <button id="grid">Grid: on</button>
    <button id="undo">Undo</button>
    <span style="flex:1"></span>
    <button id="save" class="primary" disabled>Save</button>
  </div>
  <div class="status" id="status"></div>
  <div id="stage"><div class="empty">Pick a sprite sheet on the left.</div></div>
</main>
<script>
const $=s=>document.querySelector(s);
let sheets=[],cur=null,img=null,zoom=8,colour=3,grid=true,undoStack=[],dirty=false;

function setStatus(t){ $('#status').textContent=t||''; }

fetch('/api/sheets').then(r=>r.json()).then(list=>{
  sheets=list;
  const byDir={};
  list.forEach(s=>(byDir[s.dir]=byDir[s.dir]||[]).push(s));
  const el=$('#list'); el.innerHTML='';
  for(const dir of Object.keys(byDir)){
    const h=document.createElement('div'); h.className='group'; h.textContent=dir;
    el.appendChild(h);
    byDir[dir].forEach(s=>{
      const d=document.createElement('div'); d.className='item';
      d.innerHTML=`<span>${s.name}</span><small>${s.tiles_x}×${s.tiles_y}</small>`;
      d.onclick=()=>open_(s,d); el.appendChild(d);
    });
  }
  $('#root').textContent=`${list.length} sheets`;
  const sword=list.find(s=>/sword/i.test(s.name));
  if(sword) open_(sword,[...document.querySelectorAll('.item')]
      .find(n=>n.textContent.startsWith(sword.name)));
});

function open_(s,node){
  if(dirty && !confirm('Discard unsaved changes?')) return;
  document.querySelectorAll('.item').forEach(n=>n.classList.remove('on'));
  if(node) node.classList.add('on');
  fetch('/api/image?path='+encodeURIComponent(s.path)).then(r=>r.json()).then(d=>{
    if(d.error){ setStatus('Error: '+d.error); return; }
    cur=s; img=d; undoStack=[]; dirty=false; $('#save').disabled=true;
    buildSwatches(); render();
    setStatus(`${s.path} — ${d.w}×${d.h}, ${d.depth}-bit, ${s.tiles_x}×${s.tiles_y} tiles`);
  });
}

function buildSwatches(){
  const el=$('#sw'); el.innerHTML='';
  img.palette.slice(0,1<<img.depth).forEach((c,i)=>{
    const b=document.createElement('div');
    b.className='sw'+(i===colour?' on':'');
    b.style.background=`rgb(${c[0]},${c[1]},${c[2]})`;
    b.title='Colour '+i;
    b.onclick=()=>{colour=i;buildSwatches();};
    el.appendChild(b);
  });
}

function render(){
  const st=$('#stage'); st.innerHTML='';
  const c=document.createElement('canvas');
  c.width=img.w*zoom; c.height=img.h*zoom;
  const x=c.getContext('2d');
  for(let y=0;y<img.h;y++) for(let px=0;px<img.w;px++){
    const v=img.pixels[y*img.w+px], col=img.palette[v]||[255,0,255];
    // Colour 0 is the transparent index for sprites; show the checkerboard.
    if(v===0){ continue; }
    x.fillStyle=`rgb(${col[0]},${col[1]},${col[2]})`;
    x.fillRect(px*zoom,y*zoom,zoom,zoom);
  }
  if(grid && zoom>=4){
    x.strokeStyle='rgba(128,128,160,.55)'; x.lineWidth=1;
    for(let gx=0;gx<=img.w;gx+=8){ x.beginPath();
      x.moveTo(gx*zoom+.5,0); x.lineTo(gx*zoom+.5,c.height); x.stroke(); }
    for(let gy=0;gy<=img.h;gy+=8){ x.beginPath();
      x.moveTo(0,gy*zoom+.5); x.lineTo(c.width,gy*zoom+.5); x.stroke(); }
  }
  let painting=false;
  const paint=e=>{
    const r=c.getBoundingClientRect();
    const px=Math.floor((e.clientX-r.left)/zoom), py=Math.floor((e.clientY-r.top)/zoom);
    if(px<0||py<0||px>=img.w||py>=img.h) return;
    const i=py*img.w+px;
    if(img.pixels[i]===colour) return;
    img.pixels[i]=colour; dirty=true; $('#save').disabled=false; render();
  };
  c.onpointerdown=e=>{ painting=true; c.setPointerCapture(e.pointerId);
    undoStack.push(img.pixels.slice()); if(undoStack.length>60) undoStack.shift();
    paint(e); };
  c.onpointermove=e=>{ if(painting) paint(e); };
  c.onpointerup=()=>painting=false;
  st.appendChild(c);
}

$('#zin').onclick=()=>{ zoom=Math.min(32,zoom*2); $('#zoom').textContent=zoom+'×'; if(img)render(); };
$('#zout').onclick=()=>{ zoom=Math.max(1,zoom/2); $('#zoom').textContent=zoom+'×'; if(img)render(); };
$('#grid').onclick=()=>{ grid=!grid; $('#grid').textContent='Grid: '+(grid?'on':'off'); if(img)render(); };
$('#undo').onclick=()=>{ if(!undoStack.length) return;
  img.pixels=undoStack.pop(); dirty=true; render(); };
$('#save').onclick=()=>{
  setStatus('Saving…');
  fetch('/api/save',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({path:cur.path,w:img.w,h:img.h,depth:img.depth,
      palette:img.palette,pixels:img.pixels})})
  .then(r=>r.json()).then(d=>{
    if(d.error){ setStatus('Save failed: '+d.error); return; }
    dirty=false; $('#save').disabled=true;
    setStatus('Saved '+d.path+' — rebuild with: make seasons');
  });
};
addEventListener('beforeunload',e=>{ if(dirty){ e.preventDefault(); e.returnValue=''; }});
</script></body></html>"""


def free_port(preferred=8777):
    for port in range(preferred, preferred + 40):
        with socket.socket() as s:
            if s.connect_ex(("127.0.0.1", port)) != 0:
                return port
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dir", help="path to the oracles-disasm checkout")
    ap.add_argument("--port", type=int, default=0)
    ap.add_argument("--no-browser", action="store_true")
    args = ap.parse_args()

    disasm = find_disasm(args.dir)
    sheets = list_sheets(disasm)
    if not sheets:
        raise SystemExit(f"error: no editable PNGs found under {disasm}/gfx")

    Handler.disasm = disasm
    Handler.sheets = sheets
    port = args.port or free_port()

    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", port), Handler) as srv:
        url = f"http://127.0.0.1:{srv.server_address[1]}/"
        print(f"  disassembly: {disasm}")
        print(f"  {len(sheets)} sprite sheets")
        print(f"\n  Open: {url}")
        print("  Press Ctrl+C to stop.\n")
        if not args.no_browser:
            threading.Timer(0.5, lambda: webbrowser.open(url)).start()
        try:
            srv.serve_forever()
        except KeyboardInterrupt:
            print("\n  stopped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
