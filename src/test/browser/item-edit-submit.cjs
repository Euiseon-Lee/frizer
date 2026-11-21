const fs=require('node:fs'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async()=>{
 const [fixture,output,scenario]=process.argv.slice(2);
 const browser=await chromium.launch(process.platform==='win32'?{channel:'msedge',headless:true}:{headless:true});
 const page=await browser.newPage(); const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.setContent(fs.readFileSync(fixture,'utf8').replace(/<script\b[^>]*src=[\s\S]*?<\/script>/gi,'').replace(/<link[^>]*>/g,'').replace(/<img[^>]*>/g,''));
 for(const file of ['quantity-input','date-input','warning-settings','opening-status','food-category','storage-choices','food-form','item-edit','busy-buttons'])
  await page.addScriptTag({path:`src/main/resources/static/js/${file}.js`});
 if(scenario.includes('-retrycategory-')) {
  await page.locator('#purchasedAt').fill('');
  assert.equal(await page.locator('#registrationSubmit').isEnabled(),true,'unsaved category must remain saveable after date correction');
  await page.locator('#categoryMajorCode').selectOption('kimchi');
  assert.equal(await page.locator('#registrationSubmit').isDisabled(),true,'reverting category and date must disable unchanged save');
  await page.locator('#categoryMajorCode').selectOption('soy');
  await page.locator('#categoryMinorCode').selectOption('soy_tofu');
 }
 if(scenario.includes('-reset-')) {
  await page.locator('#foodName').fill('버릴 초안');await page.locator('#memo').fill('버릴 메모');
  await page.locator('#editMergeToggle').check();await page.locator('#editMergeResults button').first().click();
 }
 if(scenario.startsWith('partial')) {await page.locator('[name=editMode][value=partial]').check();await page.locator('#quantityAmount').fill('3');}
 if(scenario.includes('-cancel-')) {
  await page.locator('#editMergeToggle').check();await page.locator('#editMergeResults button').first().click();
  await page.locator('#editMergeClear').click();await page.locator('#editMergeToggle').uncheck();
 }
 if(scenario.endsWith('merge')) {await page.locator('#editMergeToggle').check();await page.locator('#editMergeResults button').first().click();}
 else if(scenario.includes('-retrycategory-')) { /* Only the invalid date is cleared; the category change remains. */ }
 else if(scenario.includes('-categorymissing-')) {
  await page.locator('#categoryMajorCode').selectOption('soy');
  await page.locator('#registrationSubmit').click();
  assert.equal(await page.locator('#categoryMinorError').isVisible(),true);
  await page.locator('#categoryMinorCode').selectOption('soy_tofu');
 }
 else if(scenario.includes('-category-')) {await page.locator('#categoryMajorCode').selectOption('soy');await page.locator('#categoryMinorCode').selectOption('soy_tofu');}
 else if(scenario.includes('-name-')) await page.locator('#foodName').fill('새 음식명');
 else if(scenario.includes('-quantity-')) await page.locator('#quantityAmount').fill('18');
 else if(scenario.includes('-unit-')) await page.locator('#quantityUnit').fill('팩');
 else if(scenario.includes('-freezer-')) {await page.locator('[name=storageType][value=FREEZER]').check();await page.locator('#frozenAt').fill('2026-09-01');}
 else if(scenario.includes('-opened-')) {await page.locator('#openingStatus').selectOption('OPENED');await page.locator('#openedAt').fill('2026-09-01');}
 else if(scenario.includes('-unknown-')) await page.locator('#openingStatus').selectOption('OPENED');
 else if(scenario.includes('-forever-')) {await page.locator('[name=warningPaused]').check();await page.locator('[name=warningForever]').check();}
 else if(scenario.includes('-warningdate-')) {await page.locator('[name=warningPaused]').check();await page.locator('#warningPausedUntil').fill('2099-01-01');}
 else if(scenario.includes('-etc-')) {await page.locator('#sourceType').selectOption('ETC');await page.locator('#sourceMemo').fill('받은 음식');}
 else await page.locator('#memo').fill('선택한 3개 메모');
 await page.evaluate(()=>document.addEventListener('submit',event=>{
  window.captured={prevented:event.defaultPrevented,fields:[...new FormData(event.target).entries()]};event.preventDefault();
 }));
 assert.equal(await page.locator('#registrationSubmit').isEnabled(),true,'valid changed form must allow save');
 await page.locator('#registrationSubmit').click();
 const captured=await page.evaluate(()=>window.captured);
 assert.ok(captured,'save must submit');assert.equal(captured.prevented,false);
 assert.equal(captured.fields.some(([name])=>name==='categoryAction'),false,'hidden category refresh button must never be submitted');
 assert.deepEqual(errors,[]);
 fs.writeFileSync(output,JSON.stringify(captured.fields));await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
