import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,readdirSync,copyFileSync,rmSync,statSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'), files=['Code.gs','appsscript.json'];
export const norm=s=>String(s).replace(/^\uFEFF/,'').replace(/\r\n?/g,'\n').trimEnd()+'\n';
function ordered(v){return Array.isArray(v)?v.map(ordered):v&&typeof v==='object'?Object.fromEntries(Object.keys(v).sort().map(k=>[k,ordered(v[k])])):v;}
export function hash(code,manifest){const h=createHash('sha256');h.update('Code.gs\0'+norm(code)+'\0appsscript.json\0'+JSON.stringify(ordered(JSON.parse(manifest))));return h.digest('hex');}
export const exactDeployment=(s,id)=>String(s).split(/[^A-Za-z0-9_-]+/).includes(id);
export const expectedFiles=a=>a.slice().sort().join('|')===files.slice().sort().join('|');
function command(bin,args,opts={}){const r=spawnSync(bin,args,{encoding:'utf8',timeout:120000,maxBuffer:8000000,...opts});if(r.error||r.status!==0)throw Error(bin+' '+args[0]+' failed; check OAuth and Apps Script API. Exit '+r.status);return r.stdout||'';}
function source(dir){return {code:readFileSync(join(dir,'Code.gs'),'utf8'),manifest:readFileSync(join(dir,'appsscript.json'),'utf8')};}
function inHistory(remoteHash){const ids=command('git',['log','--format=%H','--','backend/Code.gs'],{cwd:root}).trim().split('\n').filter(Boolean);for(const id of ids){try{if(hash(command('git',['show',id+':backend/Code.gs'],{cwd:root}),command('git',['show',id+':backend/appsscript.json'],{cwd:root}))===remoteHash)return id;}catch{}}return null;}
function config(dir,scriptId){writeFileSync(join(dir,'.clasp.json'),JSON.stringify({scriptId,rootDir:'.',scriptExtensions:['.gs','.js']}),{mode:0o600});}
async function main(){
 const operation=process.env.BRIDGE_OPERATION||'inspect',scriptId=process.env.APPS_SCRIPT_ID||'',deploymentId=process.env.APPS_SCRIPT_DEPLOYMENT_ID||'',auth=process.env.APPS_SCRIPT_CLASPRC_JSON||'';
 if(!['inspect','deploy'].includes(operation))throw Error('Invalid operation');
 if(!/^[A-Za-z0-9_-]{20,200}$/.test(scriptId)||!/^[A-Za-z0-9_-]{20,200}$/.test(deploymentId)||!auth)throw Error('Add GitHub secrets APPS_SCRIPT_ID, APPS_SCRIPT_DEPLOYMENT_ID, APPS_SCRIPT_CLASPRC_JSON');
 try{JSON.parse(auth)}catch{throw Error('OAuth secret is not JSON')}
 const temp=mkdtempSync(join(tmpdir(),'endurance-bridge-')),home=join(temp,'home'),live=join(temp,'live'),target=join(temp,'target');
 mkdirSync(home,{mode:0o700});mkdirSync(live,{mode:0o700});mkdirSync(target,{mode:0o700});
 const env={...process.env,HOME:home};delete env.APPS_SCRIPT_CLASPRC_JSON;
 try{
  writeFileSync(join(home,'.clasprc.json'),auth,{mode:0o600});config(live,scriptId);config(target,scriptId);
  if(!exactDeployment(command('clasp',['list-deployments'],{cwd:live,env}),deploymentId))throw Error('Existing deployment ID not found; no changes made');
  command('clasp',['pull'],{cwd:live,env});
  const names=readdirSync(live).filter(f=>!f.startsWith('.')&&statSync(join(live,f)).isFile());
  if(!expectedFiles(names))throw Error('Unexpected live file set: '+names.join(', ')+'; refusing overwrite');
  const remote=source(live),next=source(join(root,'backend')),remoteHash=hash(remote.code,remote.manifest),nextHash=hash(next.code,next.manifest),baseline=inHistory(remoteHash);
  console.log('Existing deployment: confirmed');
  console.log('Remote fingerprint SHA256: '+remoteHash);
  console.log('Candidate fingerprint SHA256: '+nextHash);
  console.log('Remote source in Git history: '+(baseline?baseline.slice(0,12):'NO - BLOCKED'));
  if(operation==='inspect'){console.log('READ ONLY: nothing deployed');return;}
  if(!baseline)throw Error('Remote editor code differs from Git history; refusing overwrite');
  let expected=(process.env.BRIDGE_EXPECTED_REMOTE_SHA256||'').trim();
  const match=expected.match(/^deploy-bridge:\s*([a-f0-9]{64})(?:\s|$)/i);if(match)expected=match[1];
  if(!/^[a-f0-9]{64}$/i.test(expected)||expected.toLowerCase()!==remoteHash)throw Error('Expected remote fingerprint does not match; inspect again');
  for(const f of files)copyFileSync(join(root,'backend',f),join(target,f));
  command('clasp',['push','--force'],{cwd:target,env});
  command('clasp',['pull'],{cwd:live,env});
  const confirmed=source(live);if(hash(confirmed.code,confirmed.manifest)!==nextHash)throw Error('Read-back differs; versioned web app NOT updated');
  command('clasp',['create-deployment','--deploymentId',deploymentId,'--description','Endurance Coach GitHub '+(process.env.GITHUB_SHA||'').slice(0,12)],{cwd:target,env});
  if(!exactDeployment(command('clasp',['list-deployments'],{cwd:target,env}),deploymentId))throw Error('Could not confirm original deployment ID after update');
  console.log('SUCCESS: existing Apps Script deployment updated without changing its URL');
 }finally{rmSync(temp,{recursive:true,force:true});}
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(e=>{console.error('Bridge deployment safety: '+e.message);process.exitCode=1;});
