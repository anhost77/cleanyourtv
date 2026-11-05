(function(){if(window.tvNav)return;
var SEL='a[href],button,input,select,textarea,summary,[tabindex]:not([tabindex="-1"]),[role=button],[role=link],[role=tab],[role=menuitem],[role=option],[role=checkbox],[role=switch],[onclick]';
function vis(e){var r=e.getBoundingClientRect();if(r.width<6||r.height<6)return false;var s=getComputedStyle(e);
if(s.visibility=='hidden'||s.display=='none'||+s.opacity<0.05||s.pointerEvents=='none')return false;
var p=e;while(p&&p!==document.body){var q=getComputedStyle(p);if(q.display=='none'||q.visibility=='hidden')return false;p=p.parentElement;}
var x=Math.min(Math.max(r.left+r.width/2,1),innerWidth-1),y=Math.min(Math.max(r.top+r.height/2,1),innerHeight-1);
if(r.bottom>0&&r.top<innerHeight&&r.right>0&&r.left<innerWidth){var h=document.elementFromPoint(x,y);if(h&&!(e===h||e.contains(h)||h.contains(e)))return false;}
return true;}
function cands(){var nodes=document.querySelectorAll('body *'),all=[];
for(var i=0;i<nodes.length;i++){var e=nodes[i],ok=e.matches(SEL);
if(!ok){var c=getComputedStyle(e).cursor;if(c=='pointer'){var p=e.parentElement;ok=!p||getComputedStyle(p).cursor!='pointer';}}
if(ok&&vis(e))all.push(e);}
return all.filter(function(e){for(var i=0;i<all.length;i++){var o=all[i];if(o!==e&&e.contains(o))return false;}return true;});}
function ring(){var g=document.getElementById('tv-ring');if(!g){g=document.createElement('div');g.id='tv-ring';g.style.cssText='position:fixed;z-index:2147483647;pointer-events:none;border:4px solid #fff;border-radius:10px;box-shadow:0 0 0 4px rgba(0,0,0,.65),0 0 24px rgba(255,255,255,.5);transition:none;display:none';(document.body||document.documentElement).appendChild(g);}return g;}
function place(){var e=window.__tvCur,g=ring();if(!e||!document.contains(e)){g.style.display='none';return;}var r=e.getBoundingClientRect();g.style.display='block';g.style.left=(r.left-6)+'px';g.style.top=(r.top-6)+'px';g.style.width=(r.width+4)+'px';g.style.height=(r.height+4)+'px';}
addEventListener('scroll',function(){requestAnimationFrame(place);},true);addEventListener('resize',place);
function mark(e){if(window.__tvCur)window.__tvCur.classList.remove('tv-cur');window.__tvCur=e;e.classList.add('tv-cur');
try{e.focus({preventScroll:true});}catch(x){}e.scrollIntoView({block:'center',inline:'center',behavior:'auto'});place();setTimeout(place,120);setTimeout(place,400);}
window.tvNav=function(dir){var list=cands();if(!list.length){scrollBy(0,dir=='down'?400:dir=='up'?-400:0);return;}
var cur=window.__tvCur;if(!cur||!document.contains(cur)||!vis(cur)){cur=null;}
if(!cur){var best=null,bd=1e9;list.forEach(function(e){var r=e.getBoundingClientRect();if(r.bottom<0||r.top>innerHeight)return;
var d=Math.abs(r.top)+Math.abs(r.left)*0.3;if(d<bd){bd=d;best=e;}});mark(best||list[0]);return;}
var c=cur.getBoundingClientRect(),cx=c.left+c.width/2,cy=c.top+c.height/2,best=null,bs=1e12;
list.forEach(function(e){if(e===cur)return;var r=e.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,main,side;
if(dir=='down'){if(r.top<c.bottom-4&&y<=cy+4)return;main=Math.max(0,r.top-c.bottom);side=Math.max(0,Math.max(r.left-c.right,c.left-r.right));if(y<=cy)return;}
else if(dir=='up'){if(r.bottom>c.top+4&&y>=cy-4)return;main=Math.max(0,c.top-r.bottom);side=Math.max(0,Math.max(r.left-c.right,c.left-r.right));if(y>=cy)return;}
else if(dir=='right'){if(x<=cx+4)return;main=Math.max(0,r.left-c.right);side=Math.max(0,Math.max(r.top-c.bottom,c.top-r.bottom));}
else{if(x>=cx-4)return;main=Math.max(0,c.left-r.right);side=Math.max(0,Math.max(r.top-c.bottom,c.top-r.bottom));}
var s=main+side*3+(dir=='down'||dir=='up'?Math.abs(x-cx)*0.15:Math.abs(y-cy)*0.15);if(s<bs){bs=s;best=e;}});
if(best)mark(best);else if(dir=='down'||dir=='up')scrollBy(0,dir=='down'?400:-400);};
window.tvPoint=function(){var e=window.__tvCur;if(!e||!document.contains(e))return '';var r=e.getBoundingClientRect();
return (r.left+r.width/2)/innerWidth+','+(r.top+r.height/2)/innerHeight;};
var st=document.createElement('style');st.textContent='.tv-cur{outline:4px solid #fff!important;outline-offset:3px!important;box-shadow:0 0 0 8px rgba(0,0,0,.6)!important;}';
(document.head||document.documentElement).appendChild(st);})();