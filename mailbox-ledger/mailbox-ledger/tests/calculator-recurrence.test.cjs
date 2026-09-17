const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/calculator.js'),R=require('../app/src/main/assets/recurrence.js');
for(const [a,b] of [['18.5*2+6','43.00'],['(18.5+6)*2','49.00'],['0.1+0.2','0.30'],['1/3*3','1.00'],['1/8','0.13'],['3-5','-2.00'],['-2*-3','6.00'],['8÷2×3−1','11.00']])assert.equal(C.compute(a),b,a);
for(const a of ['1/0','1+','alert(1)','2**3','1;2'])assert.throws(()=>C.compute(a),a);
assert.equal(R.first('weekly',1,'2026-09-14'),'2026-09-14');assert.equal(R.after('weekly',1,'2026-09-14'),'2026-09-21');
assert.equal(R.first('monthly',31,'2024-02-01'),'2024-02-29');assert.equal(R.after('monthly',31,'2024-02-29'),'2024-03-31');assert.equal(R.first('monthly',31,'2025-02-01'),'2025-02-28');assert.equal(R.after('monthly',31,'2025-12-31'),'2026-01-31');
console.log('PASS exact calculator arithmetic and monthly/weekly recurrence boundaries');
