const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/js/food-filter.js','utf8');
function card(name){return {dataset:{name},hidden:false};}
function run(cards){
 const filter={value:'',listeners:{},addEventListener(n,f){this.listeners[n]=f}};
 const grid={querySelectorAll:()=>cards};
 const empty={hidden:true};
 const countBadge={textContent:String(cards.length)};
 const nodes={foodFilter:filter,foodGrid:grid,foodFilterEmpty:empty};
 const winListeners={};
 vm.runInNewContext(source,{document:{getElementById:id=>nodes[id],querySelector:()=>countBadge},
  window:{addEventListener(n,f){winListeners[n]=f}}});
 return {filter,empty,countBadge,winListeners,type(v){filter.value=v;filter.listeners.input();}};
}

// 이름 부분 일치로 카드를 필터링하고 건수를 갱신한다. 대소문자는 무시한다.
const cards=[card('두부'),card('풀무원 두부'),card('KGB 레몬')];
const s=run(cards);
s.type('두부');
assert.deepEqual(cards.map(c=>c.hidden),[false,false,true]);
assert.equal(s.countBadge.textContent,'2');assert.equal(s.empty.hidden,true);
s.type('kgb');
assert.deepEqual(cards.map(c=>c.hidden),[true,true,false]);assert.equal(s.countBadge.textContent,'1');

// 결과가 없으면 안내를 보여주고, 비우면 전체가 돌아온다.
s.type('없는 음식');
assert.ok(cards.every(c=>c.hidden));assert.equal(s.empty.hidden,false);assert.equal(s.countBadge.textContent,'0');
s.type('  ');
assert.ok(cards.every(c=>!c.hidden));assert.equal(s.empty.hidden,true);assert.equal(s.countBadge.textContent,'3');

// BFCache 복귀(pageshow)는 복원된 검색어를 다시 적용한다.
s.filter.value='레몬';s.winListeners.pageshow();
assert.deepEqual(cards.map(c=>c.hidden),[true,true,false]);

// 검색 입력이 없는 화면(빈 목록)에서는 아무것도 하지 않는다.
vm.runInNewContext(source,{document:{getElementById:()=>null,querySelector:()=>null},window:{addEventListener(){}}});

console.log('food-filter: name filtering, count sync, empty notice and pageshow reapply passed');
