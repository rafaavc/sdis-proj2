#! /usr/bin/bash

# Script for running a peer
# To be run in the root of the build tree
# No jar files used
# Assumes that Peer is the main class 
#  and that it belongs to the peer package
# Modify as appropriate, so that it can be run 
#  from the root of the compiled tree

# Check number input arguments
argc=$#

if (( argc != 2 && argc != 4 ))
then
	echo "Usage: $0 <svc_access_point> <peer_server_port> [<preexisting_peer_ip> <preexisting_peer_server_port>]"
	exit 1
fi

# Assign input arguments to nicely named variables

svc_access_point=$1
peer_server_port=$2

# Execute the program
# Should not need to change anything but the class and its package, unless you use any jar file

# echo "java peer.Peer ${ver} ${id} ${sap} ${mc_addr} ${mc_port} ${mdb_addr} ${mdb_port} ${mdr_addr} ${mdr_port}"

if (( argc == 4 ))
then
  java Main ${svc_access_point} ${peer_server_port} $3 $4
fi


if (( argc == 2 ))
then
  java Main ${svc_access_point} ${peer_server_port}
fi