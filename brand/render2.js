const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch();const p=await b.newPage();const svg=fs.readFileSync(__dirname+'/mark.svg','utf8');const mono=fs.readFileSync(__dirname+'/mono.svg','utf8');
 const jobs=JSON.parse(process.argv[2]);
 for(const j of jobs){let m=svg,w=j.size,h=j.size,bg="transparent";
  if(j.mode==="fg")m=svg.replace(/<g id="bgLayer">[\s\S]*?<\/g>/,'').replace('<g id="art" transform="translate(256 256) scale(1.02)','<g id="art" transform="translate(256 256) scale(.64)');
  if(j.mode==="round")m=svg.replace('rx="112"','rx="256"');
  if(j.mode==="mono"){m='<div style="width:'+w+'px;height:'+h+'px;-webkit-mask:url(&quot;data:image/svg+xml;base64,'+Buffer.from(mono.replace(/fill="#000"/g,'fill="transparent"').replace(/stroke="#000"/g,'stroke="transparent"')).toString('base64')+'&quot;) center/contain no-repeat;background:#fff"></div>';}
  if(j.mode==="splash"){w=j.w;h=j.h;m=`<div style="width:${w}px;height:${h}px;background:#F3F4F7;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:${j.s*.08}px"><div style="width:${j.s}px;height:${j.s}px;filter:drop-shadow(0 ${j.s*.04}px ${j.s*.08}px rgba(14,27,46,.18))">${svg.replace('<svg ','<svg width="100%" height="100%" ')}</div><div style="font:700 ${j.s*.2}px DejaVu Sans,sans-serif;color:#0E1B2E">Kharchnama</div></div>`}
  else if(j.mode!=="mono")m=m.replace('<svg ','<svg width="'+w+'" height="'+h+'" ');
  await p.setViewportSize({width:w,height:h});await p.setContent('<html><body style="margin:0;background:transparent">'+m+'</body></html>');await p.screenshot({path:j.out,omitBackground:j.mode!=="splash",clip:{x:0,y:0,width:w,height:h}})}
 await b.close()})();
