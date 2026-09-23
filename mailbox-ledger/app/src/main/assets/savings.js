'use strict';
(function(root){
 const M=root.Money||(typeof require==='function'?require('./money.js'):null);
 const rates=[1,5,10,20,30,50,80,100];
 function positive(s){if(M.parse(s,2).n<=0n)throw Error('金额必须大于0');return M.fixed(M.cents(s));}
 function calculate(base,rule){
  if(rule.mode==='fixed')return positive(rule.fixed);
  if(rule.mode!=='percent'||!rates.includes(rule.percent))throw Error('攒钱比例无效');
  const c=M.cents(positive(base)), n=c*BigInt(rule.percent); // hundredths of a cent, no premature rounding
  const step=n<=8800n?0n:(n-8800n+9999n)/10000n;
  const result=step*100n+88n;return M.fixed(result>9988n?9988n:result);
 }
 function migrate(d){
  if(d.accounts===undefined)d.accounts=[{id:'saving-source',name:'支付宝余额',currency:'CNY'},{id:'saving-target',name:'余额宝笔笔攒',currency:'CNY'}];
  if(d.savingsRule===undefined)d.savingsRule=null;
  return d;
 }
 function validateRule(r,accounts){
  if(!r||!['percent','fixed'].includes(r.mode)||!rates.includes(r.percent))throw Error('笔笔攒规则无效');
  positive(r.fixed);
  if(r.from===r.to||!accounts.some(a=>a.id===r.from)||!accounts.some(a=>a.id===r.to))throw Error('请选择两个不同的转出、转入账本');
 }
 function validate(d){
  migrate(d);if(!Array.isArray(d.accounts)||d.accounts.length>100)throw Error('账户数据无效');const ids=new Set();
  for(const a of d.accounts){if(!a||typeof a.id!=='string'||!/^[\w-]{1,80}$/.test(a.id)||ids.has(a.id)||typeof a.name!=='string'||!a.name.trim()||a.name.length>40||a.currency!=='CNY')throw Error('账户数据无效');ids.add(a.id);}
  if(d.savingsRule!==null)validateRule(d.savingsRule,d.savingsRule.scope==='books'?d.books:d.accounts);
  for(const e of d.entries){if(e.saving==null)continue;const t=e.saving;validateRule(t.rule,t.rule.scope==='books'?d.books:d.accounts);
   if(t.rule.scope==='books'){for(const side of ['from','to']){const b=d.books.find(b=>b.id===t[side]),q=t[side+'Quote'];if(!q||q.currency!==b.currency||typeof q.rate!=='string'||M.parse(q.rate).n<=0n||typeof q.updated!=='string'||typeof q.source!=='string'||(b.currency==='CNY'&&q.rate!=='1')||q.amount!==M.convert(t.amount,q.rate))throw Error('账本转账汇率或金额无效');}}
   if(e.type!=='expense'||t.currency!=='CNY'||t.from!==t.rule.from||t.to!==t.rule.to||calculate(t.base,t.rule)!==t.amount||positive(t.base)!==t.base)throw Error('笔笔攒转账数据无效');
   if(e.currency==='CNY'&&t.base!==e.amount)throw Error('笔笔攒消费基数不一致');
   const book=d.books.find(b=>b.id===e.bookId);if(e.currency!=='CNY'&&book?.currency==='CNY'&&t.base!==e.converted)throw Error('笔笔攒换算基数不一致');
  }return d;
 }
 function totals(entries,id){let incoming=0n,outgoing=0n;for(const e of entries){const t=e.saving;if(!t)continue;if(t.from===id)outgoing+=M.cents(t.amount);if(t.to===id)incoming+=M.cents(t.amount);}return {incoming:M.fixed(incoming),outgoing:M.fixed(outgoing),net:M.fixed(incoming-outgoing)};}
 function rows(d,id){return d.entries.filter(e=>e.saving?.rule.scope==='books'&&(e.saving.from===id||e.saving.to===id)).map(e=>{const t=e.saving,incoming=t.to===id,q=t[incoming?'toQuote':'fromQuote'];return {id:e.id,bookId:id,transfer:true,type:incoming?'income':'expense',category:incoming?'笔笔攒转入':'笔笔攒转出',date:e.date,created:e.created,note:e.note,currency:q.currency,amount:q.amount,converted:q.amount};});}
 function used(d,id){return d.savingsRule?.scope==='books'&&[d.savingsRule.from,d.savingsRule.to].includes(id)||d.entries.some(e=>e.saving?.rule.scope==='books'&&[e.saving.from,e.saving.to].includes(id));}
 root.Savings={rows,used,rates,calculate,positive,migrate,validate,validateRule,totals};if(typeof module!=='undefined')module.exports=root.Savings;
})(typeof window!=='undefined'?window:globalThis);
