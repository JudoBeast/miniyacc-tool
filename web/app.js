const $ = id => document.getElementById(id);
const body = () => new URLSearchParams({rules: $('rules').value, start: $('start').value, input: $('input').value});
const esc = s => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
async function call(path){
  const r = await fetch(path,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body()});
  return r.json();
}
function msg(t,cls){ const m=$('msg'); m.textContent=t; m.className=cls||''; }
function show(d){
  let ff='FIRST:\n'; for(const k in d.first) ff+=`  ${k} = { ${d.first[k].join(', ')} }\n`;
  ff+='\nFOLLOW:\n'; for(const k in d.follow) ff+=`  ${k} = { ${d.follow[k].join(', ')} }\n`;
  ff+='\nProductions:\n'; d.productions.forEach((p,i)=>ff+=`  ${i}: ${p}\n`);
  $('ff').textContent=ff;
  $('states').textContent=d.states.map(s=>`I${s.id}:\n  ${s.items.join('\n  ')}\n  transitions: ${Object.entries(s.transitions).map(([a,b])=>a+'→I'+b).join(', ')||'none'}`).join('\n\n');
  const cols=[...d.columns,...d.gotoColumns];
  let t='<table><tr><th>State</th>'+cols.map(c=>`<th>${esc(c)}</th>`).join('')+'</tr>';
  d.table.forEach((row,i)=>{ t+=`<tr><td>${i}</td>`+cols.map(c=>`<td>${esc(row[c]||'-')}</td>`).join('')+'</tr>'; });
  $('table').innerHTML=t+'</table>';
  $('conf').textContent='SLR Conflict: '+d.conflict; $('conf').className='flag '+(d.conflict?'t':'f');
  if(d.conflict) msg('Grammar is not SLR(1): '+d.conflicts.join('; '),'err');
}
function showTrace(d){
  let t='<table class="trace"><tr><th>Step</th><th>Stack</th><th>Input</th><th>Action</th></tr>';
  d.trace.forEach(s=>t+=`<tr><td>${s.step}</td><td>${esc(s.stack)}</td><td>${esc(s.input)}</td><td>${esc(s.action)}</td></tr>`);
  t+='</table><p class="'+(d.accepted?'acc':'rej')+'">'+(d.accepted?'Result: the input is accepted.':'Result: the input is rejected.')+'</p>';
  $('trace').innerHTML=t;
}
async function run(path,after){
  try{ msg('Working…'); const d=await call(path);
    if(!d.ok){ msg(d.error,'err'); return; }
    msg('Done.','ok'); after(d); $('results').scrollIntoView({behavior:'smooth'});
  }catch(e){ msg('Cannot reach the server.','err'); }
}
$('bAnalyze').onclick=()=>run('/api/analyze',show);
$('bParse').onclick=()=>run('/api/parse',d=>{show(d);showTrace(d);});
$('bGen').onclick=()=>run('/api/generate',d=>{ $('genp').hidden=false; $('genf').textContent='Saved to '+d.file; $('gensrc').textContent=d.source; });
async function ping(){ try{ const r=await fetch('/api/health'); const d=await r.json(); $('status').textContent='● '+d.status; $('status').className='status on'; }catch(e){ $('status').textContent='● server offline'; $('status').className='status off'; } }
ping(); setInterval(ping,15000);
