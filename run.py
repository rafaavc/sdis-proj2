import argparse, os, signal, subprocess, sys, time, re, socket
from threading import Thread, Lock
from subprocess import PIPE

parser = argparse.ArgumentParser()

parser.add_argument("-n", dest="n", default=5, type=int)
parser.add_argument("-v", dest="v", default="1.0", type=str)

args = parser.parse_args()

files = {}

def pollForChanges(directory="."):
    changed = False
    # print("Looking in directory: " + directory)
    for filename in os.listdir(directory):
        _fn, extension = os.path.splitext(filename)

        itemPath = directory + "/" + filename

        if (os.path.isdir(itemPath)):   # if other directory
            dirChanged = pollForChanges(itemPath)
            if (dirChanged): changed = True
            continue

        if extension != ".java": continue     # if not .java file

        fstat = os.stat(itemPath)
        stamp = fstat.st_mtime
        inode = fstat.st_ino
        # need to check also if a file that existed in the files dict no longer exists
        try:
            if files[inode]["stamp"] != stamp:
                changed = True
                files[inode] = { "stamp": stamp, "name": filename }
            
        except KeyError:
            changed = True
            files[inode] = { "stamp": stamp, "name": filename }

    return changed

def compile_peers():
    print("Compiling...")
    out = subprocess.run(["./compile.sh"], stdout=PIPE, stderr=PIPE)
    if (out.returncode == 0):
        print("SUCCESS\n")
    else:
        print(out.stderr.decode("UTF-8"))
    return out.returncode == 0

peersColors = [ '\033[33m', '\033[34m', '\033[36m', '\033[32m', '\033[35m', '\033[37m' ]

class PrintPeerOutput(Thread):
    def __init__(self, proc, lock, stdout=True):
        Thread.__init__(self)
        self.running = True
        self.proc = proc
        self.lock = lock
        self.stdout = stdout

    def run(self):
        time.sleep(.5)
        while self.running:
            text = os.read(self.proc["stdoutPipeRFD" if self.stdout else "stderrPipeRFD"], 10240).decode("UTF-8")
            if (len(text) != 0):
                name = "peer" + str(self.proc["peerId"])
                color = peersColors[self.proc["peerId"] % len(peersColors)]

                self.lock.acquire() # so that they don"t interrupt each other

                if (not self.stdout): print('\033[41m\033[1;37m', end="") # ansi color code for red background
                else: print(color, end="")
                print(name + ":", end="")
                print('\033[0m', end="")
                print(' ', end="")

                lines = text.split("\n")
                print(lines[0])

                spacer = "-"
                for _n in name: spacer += "-"

                for line in lines[1:]:
                    if line == "": continue
                    if (not self.stdout): print('\033[41m\033[1;37m', end="")
                    else: print(color, end="")
                    print(spacer + '\033[0m', end="")
                    print(' ', end="")
                    print(line)
                
                print()

                self.lock.release()

                time.sleep(.2)

    def stop(self):
        self.running = False

def get_server_port(peerId):
    return 7099 + int(peerId);

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.connect(("8.8.8.8", 80))
myip = s.getsockname()[0]
s.close()

def run_peer(peerId, first=False, firstServerPort=-1):
    runArgs = [
        "java",
        "Main",
        args.v, 
        str(peerId),
        "peer"+str(peerId),
        str(get_server_port(peerId))
    ]

    if not first:
        runArgs.append(myip)
        runArgs.append(str(firstServerPort))

    os.execvp("java", runArgs)


processes = {}
lock = Lock()


def start_peer(peerId, first=False, firstServerPort=-1):
    os.chdir("src/build")
    stdoutRead, stdoutWrite = os.pipe()
    stderrRead, stderrWrite = os.pipe()
    newpid = os.fork()
    if newpid == 0:  # peer
        os.close(stdoutRead)
        os.close(stderrRead)
        os.dup2(stdoutWrite, sys.stdout.fileno())  # stdout will be the pipe
        os.dup2(stderrWrite, sys.stderr.fileno())  # stderr will be the pipe
        run_peer(peerId, first, firstServerPort)
    else:  # parent
        os.close(stdoutWrite)
        os.close(stderrWrite)
        processes[peerId] = {
            "peerId": peerId,
            "pid": newpid,
            "stdoutPipeRFD": stdoutRead,
            "stderrPipeRFD": stderrRead
        }
    
    thread1 = PrintPeerOutput(processes[peerId], lock)
    thread2 = PrintPeerOutput(processes[peerId], lock, False)
    processes[peerId]["threads"] = [ thread1, thread2 ]
    for thread in processes[peerId]["threads"]:
        thread.setDaemon(True)
        thread.start()

    os.chdir("../..")


def stop_peer(peerId, deleteFromProcesses=False):
    stop_peer_process(processes[peerId], True)
    if (deleteFromProcesses):
        del processes[peerId]

def stop_peer_process(proc, wait=False):
    for thread in proc["threads"]:
        thread.stop()
    os.close(proc["stdoutPipeRFD"])
    os.close(proc["stderrPipeRFD"])
    os.kill(proc["pid"], signal.SIGTERM)
    if wait: os.wait()



def start_peers():
    firstServerPort = get_server_port(0)
    start_peer(0, True)
    for i in range(1, args.n):
        start_peer(i, False, firstServerPort)
    print("\nCreated processes: \n" + str(processes), end="\n\n")


def close_processes():
    for proc in processes.values():
        # if not psutil.pid_exists(proc["pid"]):
        #     print("PID " + str(proc["pid"]) + " not found.")
        #     continue
        stop_peer_process(proc)

    for proc in processes.values():
        os.wait()
    


def printTips():
    print("Insert 'exit' to close the service. Press ENTER to check for changes and recompile.\n")


pollForChanges()
if not compile_peers(): exit()
running = True

rmipid = os.fork()
if (rmipid == 0):
    os.chdir("src/build")  # needs to either have the classpath with the ClientInterface or be started in the same folder (starting in same folder)
    print("Starting RMI...")
    os.execvp("rmiregistry", ["rmiregistry"])

time.sleep(1)

printTips()

while running:
    start_peers()

    while True:
        text = input().strip()

        if text == "exit":
            running = False
            break
        elif text == "restart":
            break
        elif text == "state":
            print("Peers active: ")
            for proc in processes.values():
                print("\t" + str(proc['peerId']) + " (PID=" + str(proc['pid']) + ")")
            print()
            continue
        elif text != "":
            startMatch = re.search("^start ([0-9]+)$", text)
            if (startMatch != None):
                n = int(startMatch.group(1))
                if (n in processes):
                    print("That id is already in use!")
                    continue
                print("Starting peer with id " + str(n))
                start_peer(n, False, get_server_port(0))  # the first peer is always the one with port 0
                continue


            stopMatch = re.search("^stop ([0-9]+)$", text)
            if (stopMatch != None):
                n = int(stopMatch.group(1))
                if (not (n in processes)):
                    print("No peer uses that id!")
                    continue
                print("Stopping peer with id " + str(n))
                stop_peer(n, True)
                continue


            print()
            subprocess.run([ "./interface.sh", *text.split(" ")])
            continue
        

        if (pollForChanges()): 
            print("Found Changes")
            if (compile_peers()): # improvement: only compile the files that were changed
                break
    
    close_processes()
    if not running: os.kill(rmipid, signal.SIGTERM)
