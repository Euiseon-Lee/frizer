// Run after ItemEditIntegrationTest.actualEditRendersAndPartialPostRedirectsToNewItem.
// Uses only its isolated rendered fixture; never navigates to the running app.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
    const browser = await chromium.launch(process.platform === 'win32' ? {channel:'msedge',headless:true} : {headless:true});
    const html = fs.readFileSync('build/reports/item-edit/edit.html','utf8')
        .replace(/<script\b[^>]*src=[\s\S]*?<\/script>/gi,'').replace(/<link[^>]*>/g,'').replace(/<img[^>]*>/g,'');
    const errors = [];
    async function page(warning = 'off', noChoices = false) {
        const p = await browser.newPage({viewport:{width:360,height:800}});
        p.on('pageerror', error => errors.push(error.message));
        await p.setContent(html);
        await p.addStyleTag({path:'src/main/resources/static/css/app.css'});
        await p.evaluate(({warning,noChoices}) => {
            if (noChoices) document.querySelector('#editMergeResults').replaceChildren();
            if (warning === 'off') return;
            const data = document.querySelector('#editOriginalData');
            const original = JSON.parse(data.textContent);
            original.warningPaused = true;
            original.warningForever = warning === 'forever';
            original.warningPausedUntil = warning === 'forever' ? null : '2099-01-01';
            data.textContent = JSON.stringify(original);
            document.querySelector('[name=warningPaused]').checked = true;
            document.querySelector('[name=warningForever]').checked = original.warningForever;
            document.querySelector('#warningPausedUntil').value = original.warningPausedUntil || '';
        }, {warning,noChoices});
        for (const file of ['quantity-input','date-input','warning-settings','opening-status','food-category','storage-choices','food-form','item-edit'])
            await p.addScriptTag({path:`src/main/resources/static/js/${file}.js`});
        return p;
    }
    let p = await page();
    await p.locator('#foodName').fill('입력한 이름');
    await p.locator('#editMergeToggle').check(); await p.locator('#editMergeToggle').uncheck();
    assert.equal(await p.locator('#foodName').inputValue(),'입력한 이름');
    await p.locator('#editMergeToggle').check(); await p.locator('#editMergeResults button').first().click();
    await p.locator('#editMergeToggle').uncheck();
    assert.equal(await p.locator('#foodName').inputValue(),'입력한 이름');
    await p.locator('#editMergeToggle').check(); await p.locator('#editMergeResults button').first().click();
    await p.locator('#editMergeClear').click(); await p.locator('#foodName').fill('다시 입력한 이름');
    await p.locator('#editMergeToggle').uncheck();
    assert.equal(await p.locator('#foodName').inputValue(),'다시 입력한 이름');
    await p.close();
    p = await page('forever');
    assert.equal(await p.locator('#registrationSubmit').isDisabled(),true);
    await p.locator('#memo').fill('변경'); assert.equal(await p.locator('#registrationSubmit').isEnabled(),true);
    await p.locator('#memo').fill('원래'); assert.equal(await p.locator('#registrationSubmit').isDisabled(),true);
    await p.locator('[name=warningForever]').uncheck(); assert.equal(await p.locator('#registrationSubmit').isEnabled(),true);
    await p.locator('[name=warningForever]').check(); assert.equal(await p.locator('#registrationSubmit').isDisabled(),true);
    await p.locator('[name=editMode][value=partial]').check(); await p.locator('#quantityAmount').fill('5');
    assert.equal(await p.locator('#registrationSubmit').isDisabled(),true);
    await p.locator('#memo').fill('일부 변경'); assert.equal(await p.locator('#registrationSubmit').isEnabled(),true);
    await p.locator('[name=editMode][value=all]').check(); assert.equal(await p.locator('#registrationSubmit').isDisabled(),true);
    await p.close();
    p = await page('date');
    assert.equal(await p.locator('#registrationSubmit').isDisabled(),true);
    await p.locator('#warningPausedUntil').fill('2099-02-01'); assert.equal(await p.locator('#registrationSubmit').isEnabled(),true);
    await p.locator('#warningPausedUntil').fill('2099-01-01'); assert.equal(await p.locator('#registrationSubmit').isDisabled(),true);
    await p.close();
    p = await page(); await p.locator('#editMergeToggle').check();
    await p.locator('#editMergeSearch').fill('없는 음식 999');
    assert.equal(await p.locator('#editMergeResults button:visible').count(),0);
    assert.equal(await p.locator('#editMergeEmpty').isVisible(),true);
    assert.match(await p.locator('#editMergeEmpty').innerText(),/검색 결과가 없어/);
    assert.equal(await p.locator('#registrationSubmit').isDisabled(),true);
    await p.locator('#editMergeSearch').fill(''); assert.equal(await p.locator('#editMergeEmpty').isHidden(),true);
    await p.locator('#editMergeResults button').first().click(); assert.equal(await p.locator('#editMergeEmpty').isHidden(),true);
    await p.close();
    p = await page('off',true); await p.locator('#editMergeToggle').check();
    assert.equal(await p.locator('#editMergeEmpty').innerText(),'합칠 수 있는 다른 음식이 없어.');
    assert.equal(await p.locator('#editMergeEmpty').isVisible(),true);
    assert.equal(await p.locator('#registrationSubmit').isDisabled(),true);
    await p.locator('#editMergeToggle').uncheck(); assert.equal(await p.locator('#editMergeEmpty').isHidden(),true);
    await p.close(); assert.deepEqual(errors,[]);
    await browser.close();
    console.log('PASS: name preservation, forever/date dirty checks, partial reset, empty search and no candidates');
})().catch(error => {console.error(error);process.exit(1);});
