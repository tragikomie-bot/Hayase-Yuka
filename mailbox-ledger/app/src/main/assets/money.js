'use strict';
(function(root) {
  function parse(s,maxPlaces=18) {
    s=String(s).trim();
    if(!/^\d{1,12}(\.\d+)?$/.test(s)) throw Error('请输入有效的正数');
    const [whole,frac='']=s.split('.');
    if(frac.length>maxPlaces) throw Error('金额最多保留'+maxPlaces+'位小数');
    return {n:BigInt(whole+frac),scale:10n**BigInt(frac.length)};
  }
  function fixed(n,digits=2) { const neg=n<0n; const s=(neg?-n:n).toString().padStart(digits+1,'0'); return (neg?'-':'')+(digits?s.slice(0,-digits)+'.'+s.slice(-digits):s); }
  function convert(amount,rate) {const a=parse(amount,2),r=parse(rate);if(a.n<=0n||r.n<=0n)throw Error('金额和汇率必须大于0');const numerator=a.n*r.n*100n,denominator=a.scale*r.scale;const cents=(numerator+denominator/2n)/denominator; if(cents>99999999999999n)throw Error('换算金额过大');return fixed(cents);}
  function cents(s) {s=String(s);if(!/^\d+(\.\d{1,2})?$/.test(s))throw Error('金额格式无效');const [w,f='']=s.split('.');return BigInt(w)*100n+BigInt(f.padEnd(2,'0'));}
  function format(s) {const v=String(s).split('.');return v[0].replace(/\B(?=(\d{3})+(?!\d))/g,',')+(v.length>1?'.'+v[1]:'');}
  root.Money={parse,convert,cents,fixed,format};
  if(typeof module!=='undefined')module.exports=root.Money;
})(typeof window!=='undefined'?window:globalThis);
