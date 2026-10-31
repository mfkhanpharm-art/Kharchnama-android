// Builds www/ for the Android app from web/app.html (the same source as the Claude artifact).
const fs=require("fs"),path=require("path");
const root=__dirname,www=path.join(root,"www"),nm=p=>path.join(root,"node_modules",p);
fs.rmSync(www,{recursive:true,force:true});fs.mkdirSync(path.join(www,"vendor"),{recursive:true});
const copy=(src,dst)=>{fs.mkdirSync(path.dirname(dst),{recursive:true});fs.copyFileSync(src,dst)};
[["@capacitor/core/dist/capacitor.js","capacitor.js"],["xlsx/dist/xlsx.full.min.js","xlsx.full.min.js"],["jspdf/dist/jspdf.umd.min.js","jspdf.umd.min.js"],
 ["jspdf-autotable/dist/jspdf.plugin.autotable.min.js","jspdf.plugin.autotable.min.js"],["pdfjs-dist/build/pdf.min.js","pdf.min.js"],["pdfjs-dist/build/pdf.worker.min.js","pdf.worker.min.js"]]
 .forEach(([s,d])=>copy(nm(s),path.join(www,"vendor",d)));
copy(path.join(root,"web","native.js"),path.join(www,"vendor","native.js"));
// fonts (latin only, offline)
const fontCss=[];
[["manrope",[400,500,600,700,800]],["sora",[500,600,700]]].forEach(([fam,ws])=>ws.forEach(w=>{
  const css=fs.readFileSync(nm(`@fontsource/${fam}/${w}.css`),"utf8");
  const blocks=css.split("/*").filter(b=>/-latin-\d+-normal \*\//.test(b));
  blocks.forEach(b=>{const block="/*"+b;(block.match(/\.\/files\/[\w.-]+\.woff2?/g)||[]).forEach(f=>copy(nm(`@fontsource/${fam}/files/${path.basename(f)}`),path.join(www,"fonts",path.basename(f))));
    fontCss.push(block.replace(/\.\/files\//g,"fonts/"))})}));
fs.writeFileSync(path.join(www,"fonts.css"),fontCss.join("\n"));
let app=fs.readFileSync(path.join(root,"web","app.html"),"utf8");
app=app.replace(/<link rel="preconnect"[^>]*>\s*/,"").replace(/<link rel="stylesheet" href="https:\/\/fonts\.googleapis\.com[^>]*>/,'<link rel="stylesheet" href="fonts.css">');
app=app.replace("<script>",'<script src="vendor/capacitor.js"></script>\n<script src="vendor/native.js"></script>\n<script>');
const shell=`<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#F3F4F7">
<style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0;font:14px system-ui,sans-serif}img{max-width:100%}[hidden]{display:none!important}</style>
</head><body>
${app}
</body></html>`;
fs.writeFileSync(path.join(www,"index.html"),shell);
console.log("www built:",fs.readdirSync(www).join(", "));
