const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
(async()=>{
const fs=require('node:fs');
const server=require('node:http').createServer((req,res)=>{let name=(req.url==='/'?'index.html':req.url.slice(1));if(!/^[a-z.-]+$/.test(name)){res.writeHead(404);return res.end();}const file=path.join(__dirname,'../app/src/main/assets',name);res.setHeader('Content-Type',name.endsWith('.png')?'image/png':name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':'text/html');try{res.end(fs.readFileSync(file));}catch(e){res.writeHead(404);res.end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const base='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({executablePath:process.env.CHROME_BIN,headless:true,args:['--no-sandbox']});
const context=await browser.newContext({viewport:{width:393,height:852},deviceScaleFactor:2,isMobile:true,hasTouch:true});
await context.addInitScript(()=>{
 window.rateCalls=[];window.rateMode='ok';
 window.Android={load:()=>localStorage.getItem('db')||'',save:s=>{localStorage.setItem('db',s);return'ok';},settings:()=>'{"key":"test-key","dailyCache":true}',saveSettings:()=> 'ok',rate:s=>{const q=JSON.parse(s);window.rateCalls.push(q);setTimeout(()=>window.nativeReply(q.id,window.rateMode==='error'?{error:'网络连接失败'}:{rate:q.from==='JPY'?'0.048523':q.to==='USD'?'0.14':'7.12345',updated:(window.rateMode==='stale'?'2020-01-01':q.date)+' 10:00:00',source:'测试数据',cached:false}),40);},exportBackup:()=>{},importBackup:()=>{},openProvider:()=>{}};
});
const page=await context.newPage();let errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
await page.goto(base);await page.locator('#release-notes [data-action=close]').click();
const getDB=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('db')));
assert.equal(await page.locator('.day').count(),new Date(new Date().getFullYear(),new Date().getMonth()+1,0).getDate());
await page.screenshot({path:process.env.SHOT_DIR+'/01-empty.png',fullPage:true});
await page.evaluate(()=>{transact(()=>db.books.push({id:'savings-book',name:'储蓄账本',currency:'CNY',created:today()}));render();});
// Configure savings, then verify CNY-base handling with a foreign expense.
await page.locator('[data-page=settings]').click();await page.locator('[data-action=savings]').click();await page.locator('#saving-rule-save').click();await page.locator('[data-page=calendar]').click();
// RMB expense, remark escaping and persistence.
await page.locator('.addbtn').click();await page.locator('#amount').fill('25.60');await page.locator('#note').fill('午餐 <测试> & 咖啡');await page.locator('#entry-save').click();
assert.equal((await getDB()).entries[0].converted,'25.60');assert.equal(await page.locator('.day.recorded').count(),1);assert.equal(await page.locator('.entry-note').textContent(),'午餐 <测试> & 咖啡');assert.equal(await page.evaluate(()=>rateCalls.length),0);
// Income and totals.
await page.locator('.addbtn').click();await page.locator('#type-in').click();await page.locator('#amount').fill('8000');await page.locator('#note').fill('九月工资');await page.locator('#entry-save').click();assert.match(await page.locator('.balance').textContent(),/7,974.40/);
// Foreign currency conversion and original preservation.
await page.locator('.addbtn').click();await page.locator('#amount').fill('800');await page.locator('#entry-currency').selectOption('JPY');await page.waitForFunction(()=>document.querySelector('#fx-result').textContent.includes('38.82'));await page.locator('#entry-saving').check();await page.locator('#note').fill('日元购物 · 自动折算');await page.locator('#category').selectOption('购物');await page.locator('#entry-save').click();let db=await getDB();assert.equal(db.entries[2].converted,'38.82');assert.equal(db.entries[2].amount,'800.00');assert.equal(db.entries[2].rate,'0.048523');assert.equal(db.entries[2].saving.base,'38.82');assert.equal(db.entries[2].saving.amount,'0.88');
await page.reload();assert.equal(await page.locator('.entry').count(),4);assert.match(await page.locator('.balance').textContent(),/7,934.70/);
// Modify amount, preserve frozen quote.
await page.locator('.entry').first().click();await page.locator('[data-action=editentry]').click();await page.locator('#amount').fill('1000');await page.locator('#entry-save').click();assert.equal((await getDB()).entries[2].converted,'48.52');
// Backdate transaction date is actually sent.
await page.locator('.addbtn').click();await page.locator('#amount').fill('10');await page.locator('#entry-currency').selectOption('USD');await page.locator('#entry-date').fill('2024-02-29');await page.locator('#entry-date').dispatchEvent('change');await page.waitForFunction(()=>document.querySelector('#fx-result').textContent.includes('2024-02-29'));await page.locator('#entry-save').click();assert.equal((await getDB()).entries[3].date,'2024-02-29');assert.equal(await page.locator('.day').count(),29);assert.equal(await page.locator('.day.recorded').count(),1);
// Delete updates calendar.
await page.locator('.entry').click();await page.locator('[data-action=deleteentry]').click();await page.locator('#confirm-yes').click();assert.equal(await page.locator('.day.recorded').count(),0);
await page.locator('[data-action=today]').click();
// New USD book, independent calendar, CNY -> USD.
await page.locator('[data-page=books]').click();await page.locator('[data-action=newbook]').click();await page.locator('#book-name').fill('旅行账本');await page.locator('#book-currency').selectOption('USD');await page.locator('#book-save').click();await page.locator('[data-page=calendar]').click();assert.equal(await page.locator('.entry').count(),0);
await page.locator('.addbtn').click();await page.locator('#amount').fill('100');await page.locator('#entry-currency').selectOption('CNY');await page.waitForFunction(()=>document.querySelector('#fx-result').textContent.includes('14.00'));await page.locator('#entry-saving').check();await page.locator('#note').fill('人民币换算为美元');await page.locator('#entry-save').click();db=await getDB();assert.equal(db.entries.at(-1).converted,'14.00');assert.equal(db.entries.at(-1).bookId,db.current);assert.equal(db.entries.at(-1).saving.base,'100.00');assert.equal(db.entries.at(-1).saving.amount,'1.88');
// Query failures cannot silently save, manual rate can.
await page.evaluate(()=>rateMode='error');await page.locator('.addbtn').click();await page.locator('#amount').fill('50');await page.locator('#entry-currency').selectOption('EUR');await page.waitForFunction(()=>document.querySelector('#fx-result').textContent.includes('网络'));await page.locator('#entry-save').click();assert.equal((await getDB()).entries.length,4);assert.match(await page.locator('#entry-error').textContent(),/汇率/);await page.locator('#manual-rate-toggle').check();await page.locator('#manual-rate').fill('1.1');await page.locator('#entry-saving').check();await page.locator('#entry-saving-base').fill('400');await page.locator('#entry-save').click();assert.equal((await getDB()).entries.at(-1).converted,'55.00');assert.equal((await getDB()).entries.at(-1).rateSource,'手动汇率');assert.equal((await getDB()).entries.at(-1).saving.base,'400.00');assert.equal((await getDB()).entries.at(-1).saving.amount,'4.88');
// Stale quote requires affirmative consent.
await page.evaluate(()=>rateMode='stale');await page.locator('.addbtn').click();await page.locator('#amount').fill('10');await page.locator('#entry-currency').selectOption('CNY');await page.waitForSelector('#stale-area:not(.hidden)');await page.locator('#entry-save').click();assert.equal((await getDB()).entries.length,5);await page.locator('#accept-stale').check();await page.locator('#entry-save').click();assert.equal((await getDB()).entries.length,6);
// Converter swap and output.
await page.evaluate(()=>rateMode='ok');await page.locator('[data-page=convert]').click();await page.locator('#convert-go').click();await page.waitForFunction(()=>document.querySelector('#convert-result').textContent.includes('14.00'));await page.locator('#swap').click();assert.equal(await page.locator('#convert-from').inputValue(),'USD');assert.equal(await page.locator('#convert-to').inputValue(),'CNY');await page.locator('#convert-go').click();await page.waitForFunction(()=>document.querySelector('#convert-result').textContent.includes('712.35'));
// Backup validation rejects altered converted amount.
const good=await getDB();const invalid=JSON.parse(JSON.stringify(good));invalid.entries[0].converted='999.00';await page.evaluate(raw=>receiveBackup(raw),JSON.stringify(invalid));assert.match(await page.locator('#toast').textContent(),/无法恢复/);
await page.evaluate(raw=>receiveBackup(raw),JSON.stringify(good));await page.locator('#confirm-yes').click();assert.equal((await getDB()).entries.length,6);
// Pick original book and render user-facing sample screens (fixtures only).
await page.locator('.book-switch').click();await page.locator('[data-action=switchbook][data-id=daily]').click();
await page.screenshot({path:process.env.SHOT_DIR+'/02-calendar.png',fullPage:true});
await page.locator('.addbtn').click();await page.locator('#amount').fill('800');await page.locator('#entry-currency').selectOption('JPY');await page.waitForFunction(()=>document.querySelector('#fx-result').textContent.includes('38.82'));await page.locator('#note').fill('午饭 · 拉面与咖啡');await page.screenshot({path:process.env.SHOT_DIR+'/03-entry.png',fullPage:true});await page.locator('[data-action=close]').click();
await page.locator('[data-page=books]').click();await page.screenshot({path:process.env.SHOT_DIR+'/04-books.png',fullPage:true});
// Narrow display must not horizontally overflow.
await page.setViewportSize({width:320,height:740});await page.locator('[data-page=calendar]').click();assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
assert.deepEqual(errors,[]);await browser.close();server.close();console.log('PASS: calendar, CRUD, persistence, book isolation, FX forward/reverse, historical dates, frozen rates, errors, manual/stale rates, backup validation, 320px layout. No page errors.');
})().catch(e=>{console.error(e);process.exit(1);});
