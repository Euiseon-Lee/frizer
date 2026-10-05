const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
function setup(){
    const element=(value='')=>({value,dataset:{},events:{},attributes:{},addEventListener(n,f){this.events[n]=f;},setAttribute(k,v){this.attributes[k]=v;}});
    const start=element(),end=element(),period=element('all'),open=element('true'),q=element(),toggle=element();
    period.disabled=true;
    start.disabled=end.disabled=true;
    const presets=['all','7','30','custom'].map(value=>element(value));
    presets[1].dataset={start:'2026-09-29',end:'2026-10-05'};
    presets[2].dataset={start:'2026-09-06',end:'2026-10-05'};
    let submits=0;
    const apply={form:{requestSubmit(button){assert.equal(button,apply);submits++;}}};
    const panel=Object.assign(element(),{hidden:false,querySelectorAll:()=>[start,end]});
    const nodes={'.history-period-toggle':toggle,'.history-period-panel':panel,'[name=searchOpen]':open,'[name=q]':q,'[name=start]':start,'[name=end]':end,'#historyPeriod':period,'.history-period-actions .search-button':apply};
    const card={querySelector:s=>nodes[s],querySelectorAll:()=>presets};
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/history-period.js','utf8'),{document:{querySelector:()=>card}});
    return {start,end,period,presets,panel,q,submits:()=>submits};
}
test('period choices change draft conditions without submitting or collapsing',()=>{
    const s=setup();
    assert.equal(s.period.disabled,false);
    for(const [index,start] of [[1,'2026-09-29'],[2,'2026-09-06']]){
        s.presets[index].events.click();
        assert.equal(s.start.value,start);assert.equal(s.end.value,'2026-10-05');
        assert.equal(s.period.value,s.presets[index].value);assert.equal(s.start.disabled,false);
        assert.equal(s.presets[index].attributes['aria-pressed'],'true');
    }
    s.presets[3].events.click();assert.equal(s.period.value,'custom');assert.equal(s.start.value,'2026-09-06');
    s.start.value='2026-10-01';s.start.events.input();assert.equal(s.period.value,'custom');
    s.presets[0].events.click();assert.equal(s.period.value,'all');assert.equal(s.start.value,'');assert.equal(s.end.value,'');assert.equal(s.start.disabled,true);
    assert.equal(s.submits(),0);assert.equal(s.panel.hidden,false);
});
test('editing dates synchronizes submitted period instead of reapplying an old preset',()=>{
    const s=setup();s.presets[3].events.click();
    s.start.value='2026-09-29';s.end.value='2026-10-05';s.end.events.change();assert.equal(s.period.value,'7');
    s.end.value='2026-10-04';s.end.events.input();assert.equal(s.period.value,'custom');
    s.start.value='';s.end.value='';s.end.events.input();assert.equal(s.period.value,'all');
});
test('only the apply button submits the search form',()=>{
    const html=fs.readFileSync('src/main/resources/templates/history/list.html','utf8');
    assert.doesNotMatch(html,/onchange=/);
    assert.equal((html.match(/type="submit"/g)||[]).length,1);
    assert.equal((html.match(/type="button" value=/g)||[]).length,4);
});
