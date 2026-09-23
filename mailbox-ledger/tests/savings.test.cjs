const assert=require('node:assert/strict'),S=require('../app/src/main/assets/savings.js');
const rule={mode:'percent',percent:1,fixed:'1.00',from:'a',to:'b'};
for(const [a,b] of [['0.01','0.88'],['10','0.88'],['88','0.88'],['88.01','1.88'],['188','1.88'],['188.01','2.88'],['9988','99.88'],['999999999999.99','99.88']])assert.equal(S.calculate(a,rule),b,a);
for(const percent of S.rates){for(const cents of [1,87,88,89,187,188,189,999,10000,18800,998800]){const r={...rule,percent};const got=S.calculate((cents/100).toFixed(2),r);let expected=88;while(expected<9988&&expected*100<cents*percent)expected+=100;assert.equal(got,(expected/100).toFixed(2));}}
assert.equal(S.calculate('88',{...rule,mode:'fixed',fixed:'123.45'}),'123.45');for(const fixed of ['0','-1','1.001','abc'])assert.throws(()=>S.calculate('1',{...rule,mode:'fixed',fixed}));
assert.throws(()=>S.calculate('0',rule));assert.throws(()=>S.calculate('1',{...rule,percent:2}));
const entries=[{saving:{from:'a',to:'b',amount:'1.88'}},{saving:{from:'b',to:'a',amount:'0.88'}}];assert.deepEqual(S.totals(entries,'a'),{incoming:'0.88',outgoing:'1.88',net:'-1.00'});assert.deepEqual(S.totals(entries,'b'),{incoming:'1.88',outgoing:'0.88',net:'1.00'});
console.log('PASS percentage tiers, exact .88 boundaries, cap, fixed amount, validation, transfer conservation');
