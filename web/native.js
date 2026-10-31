/* Kharcha Android bridge: connects the web app to native features through Capacitor. */
(function(){
  "use strict";
  var Cap=window.Capacitor;
  if(!Cap||!Cap.isNativePlatform||!Cap.isNativePlatform())return;
  var reg=function(n){return Cap.registerPlugin(n)};
  var Native=reg("KharchaNative"),Notif=reg("LocalNotifications"),Files=reg("Filesystem"),Share=reg("Share"),App=reg("App"),Bar=reg("StatusBar");

  function b64(blob){return new Promise(function(res,rej){var fr=new FileReader();fr.onload=function(){var s=String(fr.result);res(s.slice(s.indexOf(",")+1))};fr.onerror=rej;fr.readAsDataURL(blob)})}
  function hash(s){var h=0;for(var i=0;i<s.length;i++){h=(h*31+s.charCodeAt(i))|0}return Math.abs(h)%2000000000+1}
  function getKey(){try{return localStorage.getItem("kharcha.aikey")||""}catch(e){return ""}}
  function getModel(){try{return localStorage.getItem("kharcha.aimodel")||"claude-sonnet-5-5"}catch(e){return "claude-sonnet-5-5"}}

  /* Faygul: Anthropic Messages API with the user's own key, streamed. */
  async function sample(input,opts){
    opts=opts||{};var key=getKey();if(!key)throw{code:"no_key",message:"No API key"};
    var msgs=typeof input==="string"?[{role:"user",content:input}]:input.map(function(m){return{role:m.role,content:m.content}});
    if(opts.images&&opts.images.length){var last=msgs[msgs.length-1];var parts=[];
      for(var i=0;i<opts.images.length;i++){var bl=opts.images[i];parts.push({type:"image",source:{type:"base64",media_type:bl.type||"image/jpeg",data:await b64(bl)}})}
      parts.push({type:"text",text:String(last.content)});last.content=parts}
    var stream=typeof opts.onText==="function";var res;
    try{res=await fetch("https://api.anthropic.com/v1/messages",{method:"POST",signal:opts.signal,headers:{"content-type":"application/json","x-api-key":key,"anthropic-version":"2023-06-01","anthropic-dangerous-direct-browser-access":"true"},
      body:JSON.stringify({model:getModel(),max_tokens:opts.maxTokens||4096,stream:stream,messages:msgs})})}
    catch(e){throw{code:"unavailable",message:"No internet connection"}}
    if(!res.ok){var t="";try{t=(await res.json()).error.message}catch(e){}throw{code:res.status===401||res.status===403?"bad_key":res.status===429?"rate_limited":"unavailable",message:t||("HTTP "+res.status)}}
    if(!stream){var j=await res.json();return{text:(j.content||[]).filter(function(c){return c.type==="text"}).map(function(c){return c.text}).join(""),truncated:j.stop_reason==="max_tokens"}}
    var rd=res.body.getReader(),dec=new TextDecoder(),buf="",text="";
    for(;;){var r=await rd.read();if(r.done)break;buf+=dec.decode(r.value,{stream:true});var idx;
      while((idx=buf.indexOf("\n\n"))>=0){var ev=buf.slice(0,idx);buf=buf.slice(idx+2);
        var line=ev.split("\n").filter(function(l){return l.indexOf("data:")===0})[0];if(!line)continue;var d;try{d=JSON.parse(line.slice(5))}catch(e){continue}
        if(d.type==="content_block_delta"&&d.delta&&d.delta.type==="text_delta"){text+=d.delta.text;opts.onText({text:text,delta:d.delta.text})}
        else if(d.type==="error")throw{code:"unavailable",message:(d.error&&d.error.message)||"Stream error"}}}
    return{text:text,truncated:false}}
  sample.limits=async function(){return{images:!!getKey()}};

  /* Files: write to app cache, then open Android's share sheet (save to Files, Drive, WhatsApp…). */
  var downloads={save:async function(o){var data=o.data instanceof Blob?await b64(o.data):btoa(unescape(encodeURIComponent(String(o.data))));
    var w=await Files.writeFile({path:o.filename,data:data,directory:"CACHE",recursive:true});
    try{await Share.share({title:o.filename,url:w.uri,dialogTitle:"Save or share "+o.filename})}catch(e){if(!/cancel/i.test(String(e&&e.message)))throw e}
    return{saved:true}}};

  /* Bill reminders as real notifications at 9:00 on the due date. */
  var notifyBusy=false;
  async function notify(list){if(notifyBusy)return;notifyBusy=true;try{
    var perm=await Notif.checkPermissions();if(perm.display!=="granted"){if(!list.length)return;perm=await Notif.requestPermissions();if(perm.display!=="granted")return}
    var pend=await Notif.getPending();if(pend.notifications&&pend.notifications.length)await Notif.cancel({notifications:pend.notifications.map(function(n){return{id:n.id}})});
    var now=Date.now(),out=[];
    list.forEach(function(x){var p=x.date.split("-").map(Number);var at=new Date(p[0],p[1]-1,p[2],9,0,0);if(at.getTime()<now){var td=new Date(),tl=td.getFullYear()+"-"+String(td.getMonth()+1).padStart(2,"0")+"-"+String(td.getDate()).padStart(2,"0");if(x.date===tl)at=new Date(now+60000);else return}
      out.push({id:hash(x.key),title:x.title,body:x.body,schedule:{at:at,allowWhileIdle:true},smallIcon:"ic_stat_kharcha",iconColor:"#0F7B5F"})});
    if(out.length)await Notif.schedule({notifications:out.slice(0,60)})}catch(e){}finally{notifyBusy=false}}

  var VENDOR={"xlsx.full.min.js":"vendor/xlsx.full.min.js","jspdf.umd.min.js":"vendor/jspdf.umd.min.js","jspdf.plugin.autotable.min.js":"vendor/jspdf.plugin.autotable.min.js","pdf.min.js":"vendor/pdf.min.js","pdf.worker.min.js":"vendor/pdf.worker.min.js"};

  window.KN={
    sample:sample,downloads:downloads,notify:notify,
    notifRequest:function(){return Notif.requestPermissions()},
    mapSrc:function(src){var f=String(src).split("/").pop();return VENDOR[f]||src},
    sms:{status:function(){return Native.smsStatus()},request:function(){return Native.requestSms()},read:function(since){return Native.readSms({since:Math.floor(since),limit:400})}},
    bio:{available:function(){return Native.biometricAvailable()},auth:function(){return Native.biometricAuth({reason:"Unlock your expense book"})}},
    start:function(app){
      try{Bar.setStyle({style:"LIGHT"});Bar.setBackgroundColor({color:"#F3F4F7"})}catch(e){}
      App.addListener("backButton",function(){if(!app.back())App.exitApp()});
      var hiddenAt=0;
      App.addListener("appStateChange",function(s){if(!s.isActive){hiddenAt=Date.now();return}if(hiddenAt&&Date.now()-hiddenAt>60000)app.relock();app.smsScan()});
      Native.addListener("sms",function(m){app.smsIngest([m])});
    }
  };
})();
