"""Verify or restore generated reference reports archived to save workspace space."""
import hashlib,lzma,os,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent/'r2_wikipedia'))
from storage_guard import within_budget
SPECS={
 'PLANNING_SESSION.original.md':('7e8f55d8c7e8a967b3f27f7aa4d02e3d4a965854d7cd2f9a455a0ff9e64ff224','70dc9a84db2dfdb1c038565eef8403ed401ca6cec290a6d708108c498356d5a9'),
 'AUTO_COMMAND_TEST_REPORT.md':('ecc03abcc21a0c5abf0321d6b1e0b32c8210338cd3ef8576c9b0a2c985b3c1e0','51fc08af068a04ebd9bfe93747ad6a844abc02513b2b9966c0bbde7db7ed1b7a'),
 'LIVE_COMMAND_AUDIT_REPORT.json':('6615c00e44d88c6d98376de724d870447bb973f9c38af515854af6ce7f58d1ff','0690e8a51e87e5abe6d9dee5e298e415e190a728777f74eb59de12104a82ee24'),
 'FIELD_DOSSIER.md':('ea6407736e022650d1086e1d44a5f76bd419ef0139cf8747309944438eed5bf3','8206971476cb2669a8effd2a3e27e41aa1028b5c89ba64f1213a9c07025c41f4'),
 'field_spec.json':('d6876e4ef6c3d645fc7fa250ae9588faebaeb7bf37b5bd0707ec87904b8bb745','524afb0c21a1b4c58dbbf1ad4220a81a3b022fc1f4744e16024921d400626df8'),
 'structure.txt':('89b69d1f6466f9d7594cc1eeb89e8903a573a1d7ed7d0c843fd41c52d02d00a4','a5dd7f0aea84c6eedce8fba1051afdf2d22c1cb4ff0883360d8844c1daba2ef0'),
 'AUTO_COMMAND_TEST_REPORT.json': ('5cfaf63af7b5c2ba8b4a8f3b10ff8b53001612b95d44528532eef25c0d74d85c','c8b5c8da60137f390037323dc1ef4328838782ac2f722516eb1b3c25b4845143'),
 'field_dossier.json': ('092a1de514a3cb5fd0af736545e0febec6202ed16647b6d5d2e386a089214871','49140830dfbdb6c2e2223b220d3668076c5e34cbdcbb6ad6e4047435ddd70b77'),
}
def digest(data):return hashlib.sha256(data).hexdigest()
def read(name):
 raw_hash,xz_hash=SPECS[name];blob=(ROOT/(name+'.xz')).read_bytes()
 if digest(blob)!=xz_hash:raise ValueError('Archive checksum mismatch')
 data=lzma.decompress(blob)
 if digest(data)!=raw_hash:raise ValueError('Report checksum mismatch')
 return data
def restore(name):
 target=ROOT/name
 if target.exists():raise FileExistsError(target)
 data=read(name)
 if within_budget(125)[1]+len(data)>=125_000_000:raise RuntimeError('125 MB guard blocks restoration')
 cache=ROOT.parent/'.cache/r2-atomic-pack-stage';cache.mkdir(parents=True,exist_ok=True)
 if os.stat(cache).st_dev!=os.stat(ROOT).st_dev:raise RuntimeError('Different staging filesystem')
 fd,tmp=tempfile.mkstemp(dir=cache,prefix='eternal-report-')
 try:
  with os.fdopen(fd,'wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
  os.link(tmp,target)
 finally:os.unlink(tmp)
 return target
if __name__=='__main__':
 import argparse
 ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('name',choices=SPECS);ap.add_argument('--restore',action='store_true');args=ap.parse_args()
 if args.restore:print('Restored:',restore(args.name))
 else:print('Verified bytes:',len(read(args.name)))
