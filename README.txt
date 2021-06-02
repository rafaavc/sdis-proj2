How to compile and run the project

The peer can be ran with the included scripts (that are located in the folder scripts):

    ./compile.sh - Compiles the peer and the TestApp. Must be executed in the source directory.

    ./cleanup.sh - Deletes the peer's file system (their non-volatile state plus the files they've backed up and restored). Must be ran in the build directory.

    ./peer.sh <peer_access_point> <peer_server_port> [<other_peer_ip> <other_peer_port>] - Initiates the peer. The last two arguments are optional. If they are not present, the peer will create a new chord ring. Must be ran in the build directory.
        peer_access_point - the identifier to which the peer will be bound in the RMI service.
        peer_server_port - the port of the server that the peer will open (to communicate with the other peers).
        other_peer_ip - the ip of a peer that is already in the chord ring.
        other_peer_server_port - the port of the server of the peer that is already in the chord ring.

    ./test.sh <peer_access_point> BACKUP|RESTORE|DELETE|RECLAIM|STATE|FINGERS [<operand1> [<operand2]] - Run the test app with a command for the peer.
        peer_access_point - the identifier to which the peer will is bound in the RMI service.
        operand1 e operand2 - arguments for the peer command. The commands possible are:
            BACKUP <file_to_backup> <replication degree> - initiates the backup protocol.
            DELETE <name_of_file_to_delete> - initiates the reclaim protocol.
            RESTORE <name_of_the_file_to_restore> - initiates the restore protocol.
            RECLAIM <amount_of_space_desired> - initiates the reclaim protocol.
            STATE - shows the peer's state (files it has backed up, ...).
            FINGERS - shows the peer's Chord finger table.

Optionally, a pool of peers can be ran with:
    python3 run.py [-n k]  (executed in the root directory)

    , where k is the number of peers
Once the script is running, test app commands acn be sent to stdin.
Additional commands
    start <peer_id>  - stops the peer  // peer ids start from 0. if you're running 4 peers, the next available peer id will be 4
    stop <peer_id>  - starts the peer
    exit - close program
    restart - restart execution
