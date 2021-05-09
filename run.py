import argparse, os, signal, subprocess, psutil, sys, time
from colorama import Fore, Back, Style
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

peersColors = [ Fore.RED, Fore.BLUE, Fore.CYAN, Fore.GREEN, Fore.MAGENTA, Fore.YELLOW, Fore.WHITE ]

class PrintPeerStdout(Thread):
    def __init__(self, proc, lock):
        Thread.__init__(self)
        self.running = True
        self.proc = proc
        self.lock = lock

    def run(self):
        time.sleep(.5)
        while self.running:
            text = os.read(self.proc["pipeRFD"], 10240).decode("UTF-8")
            if (len(text) != 0):
                name = "peer" + str(self.proc["peerId"])
                color = peersColors[self.proc["peerId"] % len(peersColors)]

                self.lock.acquire() # so that they don"t interrupt each other

                print(color, end="")
                print(name + ": ", end="")
                print(Fore.RESET, end="")

                lines = text.split("\n")
                print(lines[0])

                spacer = "-"
                for n in name: spacer += "-"
                spacer += " "

                for line in lines[1:]:
                    if line == "": continue
                    print(color + spacer + Fore.RESET, end="")
                    print(line)
                
                print()

                self.lock.release()

                time.sleep(.2)

    def stop(self):
        self.running = False


def run_peer(peerId):
    os.execvp("java", ["java", "Main", args.v, str(peerId), "peer"+str(peerId), "224.0.0.1", "7099", "224.0.0.2", "7099", "224.0.0.3", "7099"])

def start_peers():
    os.chdir("src/build")
    processes = []
    for i in range(args.n):
        r, w = os.pipe()
        newpid = os.fork()
        if newpid == 0:  # peer
            os.close(r)
            os.dup2(w, sys.stdout.fileno())  # stdout will be the pipe
            run_peer(i)
        else:  # parent
            os.close(w)
            processes.append({
                "peerId": i,
                "pid": newpid,
                "pipeRFD": r
            })
    # print("\nCreated processes: \n" + str(processes), end="\n\n")
    os.chdir("../..")
    lock = Lock()
    for proc in processes:
        thread = PrintPeerStdout(proc, lock)
        thread.setDaemon(True)
        proc["thread"] = thread
        thread.start()
    return processes

def close_processes(processes):
    for proc in processes:
        if not psutil.pid_exists(proc["pid"]):
            print("PID " + str(proc["pid"]) + " not found.")
            continue
        proc["thread"].stop()
        os.close(proc["pipeRFD"])
        os.kill(proc["pid"], signal.SIGTERM)
    for _i in processes:
        os.wait()


def printTips():
    print("Insert 'exit' to close the service. Press ENTER to check for changes and recompile.\n")


rmipid = os.fork()
if (rmipid == 0):
    os.chdir("src/build")  # needs to either have the classpath with the ClientInterface or be started in the same folder (starting in same folder)
    print("Starting RMI...")
    os.execvp("rmiregistry", ["rmiregistry"])


time.sleep(1)

pollForChanges()
if not compile_peers(): exit()
running = True

printTips()

while running:
    processes = start_peers()

    while True:
        text = input().strip()

        if text == "exit":
            running = False
            break
        elif text == "restart":
            break
        elif text != "":
            print()
            subprocess.run([ "./interface.sh", *text.split(" ")])
            continue
        

        if (pollForChanges()): 
            print("Found Changes")
            if (compile_peers()): # improvement: only compile the files that were changed
                break
    
    close_processes(processes)
    if not running: os.kill(rmipid, signal.SIGTERM)
