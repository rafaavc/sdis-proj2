# Distributed Backup Service for the Web

A peer-based distributed backup system. Each peer can request a file backup and specify a certain amount of local disk space to store other peers' files. Each file is stored with a certain replication degree in multiple peers. The Chord algorithm was implemented to look up the peer that should keep each file, requiring only average log(n) hops.

Consult the report <a href="./doc/report.pdf">here</a>.
