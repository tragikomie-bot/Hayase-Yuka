const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');const assert=require('node:assert/strict');const fs=require('fs'),path=require('path');
(async()=>{const root=path.resolve(__dirname,'../app/src/main/assets');const server=require('http').createServer((q,s)=>{const name=q.url==='/'?'index.html':q.url.slice(1);if(!/^[a-z.-]+$/.test(name)){s.writeHead(404);return s.end();}s.setHeader('Content-Type',name.endsWith('.css')?'text/css':name.endsWith('.js')?'application/javascript':name.endsWith('.png')?'image/png':'text/html');s.end(fs.readFileSync(path.join(root,name)));});await new Promise(r=>server.listen(0,'127.0.0.1',r));const b=await chromium.launch({executablePath:process.env.CHROME_BIN,args:['--no-sandbox']});const p=await b.newPage({viewport:{width:393,height:852},deviceScaleFactor:2,locale:'zh-CN',colorScheme:'light'});const errors=[];p.on('pageerror',e=>errors.push(e.message));p.on('dialog',d=>d.accept());await p.goto('http://127.0.0.1:'+server.address().port);await p.locator('#release-notes [data-action=close]').click();

const db=()=>p.evaluate(()=>JSON.parse(localStorage.getItem('qinglan')));
const setup=()=>p.evaluate(()=>{db.books.push({id:'target',name:'攒钱账本',currency:'CNY',created:today()});transact(()=>{});render();});
await setup();
await p.locator('[data-page=settings]').click();await p.locator('[data-action=savings]').click();
assert.equal(await p.locator('#saving-from option').count(),2);assert.match(await p.locator('#saving-from').textContent(),/攒钱账本/);
await p.locator('#saving-rule-save').click();assert.equal((await db()).savingsRule.scope,'books');
const source=(await db()).current;
await p.locator('.addbtn').click();await p.locator('#amount').fill('88');await p.locator('#entry-saving').check();await p.locator('#entry-save').click();
assert.equal((await db()).entries[0].saving.amount,'0.88');assert.match(await p.locator('.balance').textContent(),/-88.88/);assert.equal(await p.locator('.entry').count(),2);assert.match(await p.locator('.stat-num.out').textContent(),/88.00/);
await p.evaluate(()=>{transact(()=>db.current='target');render();});assert.match(await p.locator('.balance').textContent(),/0.88/);assert.match(await p.locator('.entry').textContent(),/笔笔攒转入/);assert.match(await p.locator('.day.selected').textContent(),/\+0.88/);
await p.locator('.entry').click();await p.locator('[data-action=editentry]').click();await p.locator('#amount').fill('88.01');await p.locator('#entry-save').click();assert.equal((await db()).current,source);assert.match(await p.locator('.balance').textContent(),/-89.89/);assert.equal((await db()).entries.length,1);
await p.locator('[data-page=settings]').click();await p.locator('[data-action=savings]').click();await p.locator('#saving-mode').selectOption('fixed');await p.locator('#saving-fixed').fill('123.45');await p.locator('#saving-rule-save').click();
await p.locator('.saving-history').click();await p.locator('[data-action=editentry]').click();await p.locator('#note').fill('保留旧比例');await p.locator('#entry-save').click();assert.equal((await db()).entries[0].saving.amount,'1.88');
await p.locator('.addbtn').click();await p.locator('#amount').fill('10');await p.locator('#entry-saving').check();await p.locator('#entry-save').click();assert.equal((await db()).entries[1].saving.amount,'123.45');
await p.locator('.entry[data-action=detail]').first().click();await p.locator('[data-action=editentry]').click();await p.locator('#entry-saving').uncheck();await p.locator('#entry-save').click();assert.equal((await db()).entries[1].saving,undefined);
await p.locator('.entry[data-action=detail]').last().click();await p.locator('[data-action=deleteentry]').click();await p.locator('#confirm-yes').click();assert.equal((await db()).entries.filter(e=>e.saving).length,0);assert.equal(await p.evaluate(()=>Savings.rows(db,'target').length),0);
await p.locator('[data-page=settings]').click();await p.locator('[data-action=savings]').click();await p.locator('#saving-to').selectOption(source);await p.locator('#saving-rule-save').click();assert.match(await p.locator('#saving-rule-error').textContent(),/不同/);await p.locator('#saving-to').selectOption('target');await p.locator('#saving-rule-save').click();
assert.equal(await p.evaluate(()=>Savings.used(db,'target')),true);
await p.screenshot({path:process.env.SHOT_DIR+'/savings-books-settings.png',fullPage:true});
// Foreign book quotes are frozen, not mistaken for CNY.
await p.evaluate(()=>{db.books.push({id:'usd',name:'美元攒钱',currency:'USD',created:today()});transact(()=>{});renderSavings();});
await p.locator('#saving-to').selectOption('usd');await p.locator('#saving-rule-save').click();
await p.locator('.addbtn').click();await p.locator('#amount').fill('10');await p.locator('#entry-saving').check();await p.locator('#saving-rate-to').fill('0.14');await p.locator('#entry-save').click();assert.equal((await db()).entries.at(-1).saving.toQuote.amount,'17.28');
let good=await db(),bad=structuredClone(good);bad.entries.at(-1).saving.toQuote.amount='123.45';await p.evaluate(raw=>receiveBackup(raw),JSON.stringify(bad));assert.match(await p.locator('#toast').textContent(),/无法恢复/);
await p.reload();assert.equal((await db()).entries.at(-1).saving.toQuote.rate,'0.14');
await p.evaluate(()=>{transact(()=>db.current='usd');render();});assert.match(await p.locator('.balance').textContent(),/17.28/);await p.screenshot({path:process.env.SHOT_DIR+'/savings-books-target.png',fullPage:true});
await p.setViewportSize({width:320,height:740});assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
// Legacy account records survive without silently affecting book balances.
await p.evaluate(()=>{const e=db.entries.at(-1);e.saving.rule={mode:'percent',percent:1,fixed:'1.00',from:'saving-source',to:'saving-target'};e.saving.from='saving-source';e.saving.to='saving-target';e.saving.amount='0.88';delete e.saving.fromQuote;delete e.saving.toQuote;transact(()=>{});render();});
assert.equal(await p.evaluate(()=>Savings.rows(db,'usd').length),0);assert.equal((await db()).entries.at(-1).saving.amount,'0.88');
assert.deepEqual(errors,[]);await b.close();server.close();console.log('PASS existing-book selection, debit/credit calendar and balances, no spending duplication, edit/delete/uncheck linkage, frozen rules, fixed amount, different-book guard, referenced-book guard, foreign quote freeze/backup validation, persistence, legacy preservation, mobile layout');
})().catch(e=>{console.error(e);process.exit(1);});
