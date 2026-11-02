const fs = require("fs");
const vm = require("vm");
const assert = require("node:assert/strict");

const html = fs.readFileSync("app/src/main/assets/index.html", "utf8");
const scripts = Array.from(html.matchAll(/<script>([\s\S]*?)<\/script>/g), match => match[1]);
assert.ok(scripts.length >= 2, "inline save script not found");
const saveCode = scripts[0];

function localStorageFor(map) {
  return {
    get length() { return map.size; },
    key(i) { return Array.from(map.keys())[i] ?? null; },
    getItem(k) { return map.has(k) ? map.get(k) : null; },
    setItem(k,v) { map.set(String(k),String(v)); },
    removeItem(k) { map.delete(k); },
    clear() { map.clear(); }
  };
}

let disk = "";
const native = {
  load() { return disk; },
  save(json) { disk = json; }
};
function start(storage) {
  const win = { AndroidSave:native, addEventListener(){} };
  const sandbox = {window:win,localStorage:storage,document:{addEventListener(){}},setInterval(){},console};
  vm.runInNewContext(saveCode,sandbox);
  return win.WitchSave;
}

const original = new Map();
const app = start(localStorageFor(original));
const saveKey = "https://appassets.androidplatform.net/assets/game.swf/witchgirl_data";
original.set(saveKey, "state=A&level=3");
app.snapshot(true);
let parsed = JSON.parse(disk);
assert.equal(parsed.items[saveKey], "state=A&level=3");

const lost = new Map();
const restored = start(localStorageFor(lost));
assert.equal(lost.get(saveKey), "state=A&level=3", "app-private storage restore failed");
assert.equal(JSON.parse(restored.snapshot(true)).items[saveKey],"state=A&level=3");

const replacement = JSON.stringify({version:1,items:{[saveKey]:"state=B&level=9"}});
assert.equal(restored.importBackup(replacement),true);
assert.equal(lost.get(saveKey),"state=B&level=9","user import failed");
assert.equal(JSON.parse(disk).items[saveKey],"state=B&level=9","native import backup failed");
assert.equal(restored.importBackup("INVALID"),false, "corrupt backup was accepted");

const afterReboot = new Map();
start(localStorageFor(afterReboot));
assert.equal(afterReboot.get(saveKey),"state=B&level=9", "import did not survive reboot");
console.log("PASS: automatic snapshot, restore after WebView storage loss, import, corrupt-save rejection and cold restart");
