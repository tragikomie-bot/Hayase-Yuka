const assert=require('node:assert/strict'),S=require('../app/src/main/assets/schedule-summary.js');
const e=(currency,amount,date='2026-10-12',type='expense',scheduleId='r')=>({currency,amount,date,type,scheduleId});
const rows=[e('USD','0.10'),e('USD','0.20'),e('CNY','10'),e('JPY','100'),e('EUR','99','2026-09-30'),e('USD','99','2026-11-01'),e('USD','999','2026-10-01','income'),e('USD','888','2026-10-01','expense',null)];rows[0].saving={amount:'99.88'};
assert.deepEqual(S.collect(rows,'2026-10-12'),{month:'2026-10',count:4,rows:[{currency:'CNY',amount:'10.00'},{currency:'JPY',amount:'100.00'},{currency:'USD',amount:'0.30'}]});
assert.equal(S.collect(rows,'2026-11-01').count,1);assert.equal(S.collect(rows,'2027-01-01').count,0);
assert.equal(S.convert('0.30','7.12345'),'2.14');assert.equal(S.convert('0.01','0.5'),'0.01');assert.equal(S.convert('1999999999999.98','1'),'1999999999999.98');assert.throws(()=>S.convert('10','0'));assert.throws(()=>S.convert('10','invalid'));
console.log('PASS month filtering, original-currency aggregation, no transfers/manual/income, exact cents, month/year rollover, large totals and rate validation');
