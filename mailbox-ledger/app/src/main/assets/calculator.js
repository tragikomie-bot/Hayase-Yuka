'use strict';
(function(root){
function compute(text){
 const s=String(text).replace(/\s/g,'').replace(/×/g,'*').replace(/÷/g,'/').replace(/−/g,'-');
 if(!s||s.length>120||!/^[0-9.+\-*/()]+$/.test(s))throw Error('请输入加减乘除算式');
 let pos=0;
 function gcd(a,b){a=a<0n?-a:a;while(b){const r=a%b;a=b;b=r;}return a||1n;}
 function frac(n,d){if(d===0n)throw Error('不能除以0');if(d<0n){n=-n;d=-d;}const g=gcd(n,d);return {n:n/g,d:d/g};}
 function op(a,b,c){if(c==='+')return frac(a.n*b.d+b.n*a.d,a.d*b.d);if(c==='-')return frac(a.n*b.d-b.n*a.d,a.d*b.d);if(c==='*')return frac(a.n*b.n,a.d*b.d);return frac(a.n*b.d,a.d*b.n);}
 function atom(){if(s[pos]==='+'){pos++;return atom();}if(s[pos]==='-'){pos++;const a=atom();return frac(-a.n,a.d);}if(s[pos]==='('){pos++;const a=expression();if(s[pos++]!==')')throw Error('括号不完整');return a;}const m=s.slice(pos).match(/^(?:\d+(?:\.\d*)?|\.\d+)/);if(!m)throw Error('算式不完整');pos+=m[0].length;const [w,f='']=m[0].split('.');return frac(BigInt((w||'0')+f),10n**BigInt(f.length));}
 function term(){let a=atom();while(s[pos]==='*'||s[pos]==='/'){const c=s[pos++];a=op(a,atom(),c);}return a;}
 function expression(){let a=term();while(s[pos]==='+'||s[pos]==='-'){const c=s[pos++];a=op(a,term(),c);}return a;}
 const a=expression();if(pos!==s.length)throw Error('请检查算式');const sign=a.n<0n?-1n:1n,n=(a.n<0n?-a.n:a.n)*100n;const cents=(n+a.d/2n)/a.d;if(cents>99999999999999n)throw Error('结果过大');const v=cents.toString().padStart(3,'0');return (sign<0n&&cents!==0n?'-':'')+v.slice(0,-2)+'.'+v.slice(-2);
}
root.Calculator={compute};if(typeof module!=='undefined')module.exports=root.Calculator;
})(typeof window!=='undefined'?window:globalThis);
