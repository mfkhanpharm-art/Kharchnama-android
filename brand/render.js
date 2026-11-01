const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch();const p=await b.newPage();
 const svg=fs.readFileSync(__dirname+'/mark.svg','utf8');
 async function shot(markup,size,out){await p.setViewportSize({width:size,height:size});await p.setContent('<html><body style="margin:0;background:transparent">'+markup.replace('<svg ','<svg width="'+size+'" height="'+size+'" ')+'</body></html>');await p.screenshot({path:out,omitBackground:true,clip:{x:0,y:0,width:size,height:size}})}
 const jobs=JSON.parse(process.argv[2]);
 for(const j of jobs){let m=svg;
   if(j.mode==="fg"){m=m.replace(/<g id="bgLayer">[\s\S]*?<\/g>/,'').replace('<g id="art">','<g id="art" transform="translate(256 262) scale(.62) translate(-256 -262)">')}
   if(j.mode==="mono"){m=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><defs><mask id="mk"><rect width="512" height="512" fill="#000"/><path d="M256 412c-50-28-104-32-168-16V196c64-16 118-12 168 18 50-30 104-34 168-18v200c-64-16-118-12-168 16z" fill="#fff"/><path d="M256 230v166" stroke="#000" stroke-width="18" stroke-linecap="round"/><circle cx="256" cy="120" r="74" fill="#fff"/><path d="M256 80c16 16 27 31 27 47 0 11-7 20-16 25l-11 16-11-16c-9-5-16-14-16-25 0-16 11-31 27-47z" fill="#000"/></mask></defs><rect width="512" height="512" fill="#fff" mask="url(#mk)"/></svg>`}
   if(j.mode==="splash"){m=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${j.w} ${j.h}"><rect width="${j.w}" height="${j.h}" fill="#F3F4F7"/><g transform="translate(${(j.w-j.s)/2} ${(j.h-j.s)/2-j.s*.12}) scale(${j.s/512})">${svg.replace(/^[\s\S]*?<defs>/,'<defs>').replace('</svg>','')}</g><text x="${j.w/2}" y="${(j.h+j.s)/2+j.s*.12}" text-anchor="middle" font-family="DejaVu Sans,sans-serif" font-weight="700" font-size="${j.s*.2}" fill="#0E1B2E">Kharchnama</text></svg>`;
     await p.setViewportSize({width:j.w,height:j.h});await p.setContent('<html><body style="margin:0">'+m.replace('<svg ','<svg width="'+j.w+'" height="'+j.h+'" ')+'</body></html>');await p.screenshot({path:j.out,clip:{x:0,y:0,width:j.w,height:j.h}});continue}
   await shot(m,j.size,j.out)}
 await b.close()})();
