import test from 'node:test';
import assert from 'node:assert/strict';
import {hash,norm,exactDeployment,expectedFiles} from './apps_script_ci.mjs';
test('normalises CRLF and manifest order',()=>assert.equal(hash('a\r\n','{"a":1,"b":2}'),hash('a\n','{"b":2,"a":1}')));
test('code edits change fingerprint',()=>assert.notEqual(hash('a','{}'),hash('b','{}')));
test('deployment IDs must match exactly',()=>{assert.equal(exactDeployment('AKfycb12345678901234567890 @7','AKfycb12345678901234567890'),true);assert.equal(exactDeployment('AKfycb12345678901234567890extra @7','AKfycb12345678901234567890'),false);});
test('unexpected files block deployment',()=>{assert.equal(expectedFiles(['Code.gs','appsscript.json']),true);assert.equal(expectedFiles(['Code.gs','appsscript.json','Extra.gs']),false);});
test('normalises code line endings only',()=>assert.equal(norm('a\r\n b\r\n'),'a\n b\n'));
