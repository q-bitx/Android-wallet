from pathlib import Path
import ctypes as C,hashlib,hmac,json
import argparse
parser=argparse.ArgumentParser(description="Public indexed-account oracle; never fund this known seed")
parser.add_argument("--library",required=True,help="Compiled reference ML-DSA-65 C library")
parser.add_argument("--output",required=True)
args=parser.parse_args()
lib=C.CDLL(args.library)
f=lib.pqcrystals_dilithium3_ref_seed_keypair
mnemonic=' '.join(['abandon']*23+['art'])
master=hashlib.pbkdf2_hmac('sha512',mnemonic.encode(),b'mnemonic',2048)
rows=[]
for index in list(range(128))+[255,1000,10000]:
 key_seed=hmac.new(master,f'Q-BitX/PQ-HD/v1/mainnet/account/0/receive/{index}/Dilithium3'.encode(),hashlib.sha512).digest()[:32]
 pk,sk=C.create_string_buffer(1952),C.create_string_buffer(4032)
 assert f(pk,sk,key_seed)==0
 rows.append({'index':index,'keySeed':key_seed.hex(),'publicHash':hashlib.sha256(pk.raw).hexdigest(),'secretHash':hashlib.sha256(sk.raw).hexdigest()})
Path(args.output).write_text(json.dumps(rows,indent=2))
print(json.dumps({'independent_python_c_oracle':len(rows),'root':'public zero entropy BIP39 fixture','funds':False}))
