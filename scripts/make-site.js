// Builds the GitHub Pages site the app updates from: the web bundle, version.json, the APK and a download page.
const fs=require("fs"),path=require("path");
const apk=process.argv[2];const root="android/app/src/main/assets/public";const site="site";
fs.rmSync(site,{recursive:true,force:true});fs.cpSync(root,site,{recursive:true});
const files=[];(function walk(d){for(const f of fs.readdirSync(d)){const fp=path.join(d,f);if(fs.statSync(fp).isDirectory())walk(fp);else files.push(path.relative(site,fp).split(path.sep).join("/"))}})(site);
const nativeVer=+(fs.readFileSync("web/native.js","utf8").match(/NATIVE_VER=(\d+)/)||[0,1])[1];
const build=+(process.env.GITHUB_RUN_NUMBER||0);
const notes=String(process.env.NOTES||"").split("\n").filter(l=>l.trim()&&!/^(Co-Authored-By|Claude-Session):/i.test(l)).join("\n").slice(0,600);
if(apk&&fs.existsSync(apk))fs.copyFileSync(apk,path.join(site,"Kharchnama.apk"));
fs.writeFileSync(path.join(site,"version.json"),JSON.stringify({web:build,native:nativeVer,apk:"Kharchnama.apk",notes,files,built:new Date().toISOString()},null,1));
fs.writeFileSync(path.join(site,"get.html"),`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Get Kharchnama</title>
<style>body{margin:0;font:16px/1.5 system-ui,sans-serif;background:#F3F4F7;color:#0E1726}main{max-width:480px;margin:0 auto;padding:40px 20px}h1{font-size:26px;margin:0 0 6px}p{color:#475467}a.b{display:block;text-align:center;background:#0E1B2E;color:#fff;text-decoration:none;font-weight:700;padding:16px;border-radius:14px;margin:24px 0}ol{color:#475467;padding-left:20px}small{color:#667085}</style></head>
<body><main><h1>Kharchnama</h1><p>Personal expense book with Faygul, your financial adviser. Build ${build}.</p>
<a class="b" href="Kharchnama.apk">Download for Android</a>
<ol><li>Open the downloaded file.</li><li>If Android asks, allow installs from your browser.</li><li>Tap Install. Updating keeps your data.</li></ol>
<small>Built ${new Date().toISOString().slice(0,10)}</small></main></body></html>`);
console.log("site ready:",files.length,"app files, build",build,"native",nativeVer);
