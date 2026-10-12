'use strict';
(function(root){
 const M=root.Money||(typeof require==='function'?require('./money.js'):null);
 function collect(entries,date){
  const month=date.slice(0,7),byCurrency=new Map();let count=0;
  for(const e of entries){if(!e.scheduleId||e.type!=='expense'||e.date.slice(0,7)!==month)continue;count++;byCurrency.set(e.currency,(byCurrency.get(e.currency)||0n)+M.cents(e.amount));}
  const rows=[...byCurrency].sort(([a],[b])=>a==='CNY'?-1:b==='CNY'?1:a.localeCompare(b)).map(([currency,cents])=>({currency,amount:M.fixed(cents)}));
  return {month,count,rows};
 }
 // Totals may exceed the per-entry amount limit. Keep integer cents throughout.
 function convert(amount,rate){const r=M.parse(rate);if(r.n<=0n)throw Error('汇率必须大于0');return M.fixed((M.cents(amount)*r.n+r.scale/2n)/r.scale);}
 root.ScheduleSummary={collect,convert};if(typeof module!=='undefined')module.exports=root.ScheduleSummary;
})(typeof window!=='undefined'?window:globalThis);

function mountScheduleSummary(){
 const host=document.getElementById('schedule-month-summary');if(!host)return;
 const date=today(),summary=ScheduleSummary.collect(db.entries,date),fingerprint=JSON.stringify(summary);
 host.dataset.date=date;
 host.innerHTML=`<h3>${esc(summary.month)} · 本月定时支出</h3><div class="help">全部账本 · 已入账 ${summary.count} 笔。按记账日期和原币金额统计；不含手工记账、收入、尚未执行的计划及笔笔攒转账。</div><div id="schedule-month-currencies">${summary.rows.map(r=>`<div class="detail-row"><span>${esc(r.currency)}</span><strong style="overflow-wrap:anywhere;min-width:0">${esc(Money.format(r.amount))}</strong></div>`).join('')||'<p class="help">本月还没有已入账的定时支出。</p>'}</div><button id="schedule-month-convert" class="secondary" ${summary.count?'':'disabled'}>按当天汇率查看人民币合计</button><div id="schedule-month-result" class="help" role="status" aria-live="polite"></div>`;
 const button=$('schedule-month-convert'),result=$('schedule-month-result');
 const current=()=>host.isConnected&&$('schedule-month-convert')===button&&today()===date&&JSON.stringify(ScheduleSummary.collect(db.entries,today()))===fingerprint;
 button.onclick=async()=>{
  button.disabled=true;result.textContent='正在查询 '+date+' 的汇率…';
  // Limit concurrent requests and fetch each original currency only once.
  let index=0;const results=new Array(summary.rows.length);
  async function worker(){while(index<summary.rows.length){const i=index++,row=summary.rows[i];try{const quote=await askRate(row.currency,'CNY',date,true);if(typeof quote.updated!=='string'||!quote.updated||typeof quote.source!=='string')throw Error('汇率信息不完整');results[i]={...row,quote,converted:ScheduleSummary.convert(row.amount,quote.rate)};}catch(e){results[i]={...row,error:e.message||'汇率查询失败'};}}}
  await Promise.all(Array.from({length:Math.min(3,summary.rows.length)},worker));
  if(!current()){if(host.isConnected&&$('schedule-month-convert')===button)mountScheduleSummary();return;}
  const failed=results.filter(r=>r.error),stale=results.some(r=>r.quote&&r.quote.updated.slice(0,10)!==date);
  const details=results.map(r=>r.error?`<p>${esc(r.currency)}：${esc(r.error)}</p>`:`<p>${esc(r.currency)} ${esc(Money.format(r.amount))} → CNY ${esc(Money.format(r.converted))}<br>1 ${esc(r.currency)} = ${esc(r.quote.rate)} CNY · ${esc(r.quote.updated)} · ${esc(r.quote.source)}</p>`).join('');
  result.innerHTML=failed.length?`<div class="notice error">部分币种查询失败，暂不显示人民币总额，避免遗漏。请重试。</div>${details}`:`<div class="rate-result"><div>${stale?'按最新可用参考汇率折合':'按查询当天汇率折合'}</div><strong id="schedule-month-cny">¥${esc(Money.format(Money.fixed(results.reduce((sum,r)=>sum+Money.cents(r.converted),0n))))}</strong><p>查询日期：${esc(date)}${stale?'；部分汇率非当天，请查看实际日期。':''}<br>仅供查看，不改变已保存账目。</p></div>${details}`;
  button.disabled=false;button.textContent='重新查询人民币合计';
 };
}
function refreshScheduleSummaryDate(){const host=document.getElementById('schedule-month-summary');if(host&&host.dataset.date!==today())mountScheduleSummary();}
if(typeof window!=='undefined'){
 setInterval(refreshScheduleSummaryDate,30000);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshScheduleSummaryDate();});
 window.addEventListener('focus',refreshScheduleSummaryDate);
}
