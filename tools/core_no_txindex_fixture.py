"""Creates only fresh, disconnected regtest data; never reads production configuration.
Generated wallet/block data are retained beside the output for independent review.
Example: python tools/core_no_txindex_fixture.py --core /path/to/qbitxd --output /tmp/qbx-audit/core-no-txindex.json
"""
from pathlib import Path
import json,subprocess,secrets,socket,time,urllib.request,urllib.error,base64
import argparse
parser=argparse.ArgumentParser(description="Create a public PQ transfer fixture on an isolated regtest node without txindex")
parser.add_argument("--core",type=Path,required=True)
parser.add_argument("--output",type=Path,required=True)
args=parser.parse_args()
core=args.core.resolve()
fixture_path=args.output.resolve()
out=fixture_path.parent
out.mkdir(parents=True,exist_ok=True)
data=out/('regtest-'+secrets.token_hex(4));data.mkdir()
with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
pwd=secrets.token_hex(24)
proc=subprocess.Popen([str(core),'-regtest',f'-datadir={data}',f'-rpcport={port}','-rpcuser=balance-test',f'-rpcpassword={pwd}','-rpcbind=127.0.0.1','-rpcallowip=127.0.0.1','-listen=0','-dnsseed=0','-discover=0','-connect=0','-txindex=0','-server=1','-printtoconsole=0'],creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
def rpc(m,*p,wallet=None):
 req=urllib.request.Request(f'http://127.0.0.1:{port}/'+('wallet/'+wallet if wallet else ''),data=json.dumps({'jsonrpc':'2.0','id':1,'method':m,'params':p}).encode(),headers={'Authorization':'Basic '+base64.b64encode(('balance-test:'+pwd).encode()).decode(),'Content-Type':'application/json'})
 try:
  with urllib.request.urlopen(req,timeout=45) as r:obj=json.load(r)
 except urllib.error.HTTPError as e:obj=json.load(e)
 if obj.get('error'):raise RuntimeError(obj['error'])
 return obj['result']
try:
 for _ in range(100):
  try:assert rpc('getblockchaininfo')['chain']=='regtest';break
  except Exception:
   if proc.poll() is not None:raise RuntimeError('Core exited')
   time.sleep(.2)
 rpc('createwallet','audit',False,False,'',False,True)
 mine=rpc('getnewlegacypqaddress',wallet='audit');dest=rpc('getnewdilithiumaddress',wallet='audit')
 start=int(time.time())
 for height in range(1,102):
  rpc('setmocktime',start+height*600)
  assert len(rpc('generatetoaddress',1,mine,1000000))==1
  if height%25==0:print('Isolated test blocks:',height,flush=True)
 txid=rpc('pqsendtoaddress',mine,dest,1.123,wallet='audit')['txids'][0]
 rpc('setmocktime',start+102*600)
 block=rpc('generatetoaddress',1,mine,1000000)[0]
 try:rpc('getrawtransaction',txid,True);plain='unexpected success'
 except Exception as e:plain=str(e)
 tx=rpc('getrawtransaction',txid,True,block)
 assert tx['in_active_chain'] and tx['blockhash']==block and not any('coinbase' in v for v in tx['vin'])
 scan=rpc('scantxoutset','start',['addr('+dest+')'])
 assert abs(scan['total_amount']-1.123)<1e-10
 cbid=rpc('getblock',block,1)['tx'][0];cb=rpc('getrawtransaction',cbid,True,block)
 assert 'coinbase' in cb['vin'][0] and cb['in_active_chain']
 fixture={'normal':{'txid':txid,'height':102,'blockhash':block,'transaction':tx,'scan':scan},'coinbase':{'txid':cbid,'height':102,'blockhash':block,'transaction':cb},'plain_lookup_error':plain,'txindex':False}
 fixture_path.write_text(json.dumps(fixture,indent=2))
 print(json.dumps({'normal_amount':scan['total_amount'],'normal_confirmations':tx['confirmations'],'active_chain':tx['in_active_chain'],'coinbase_detected':True,'without_block_error':plain,'txindex':False}))
finally:
 try:rpc('stop')
 except Exception:proc.terminate()
 try:proc.wait(timeout=15)
 except subprocess.TimeoutExpired:proc.kill();proc.wait()


